// @test A-LLT-032
// @requirements LLR-AND-CALL-007
// @environment native-display
// @plan Use a disposable emulator and local photo HTTP peer. Count visible changes and image requests
// during idle/unchanged polls; hold status/image/upload replies across lease expiry, call end and
// enrollment replacement. Inspect the controller on the main thread and drain worker completions.
// @pass Unchanged polls neither repaint nor redownload. Expired status never fetches an image;
// blocked or late responses cannot restore expired/ended shares. Queued cleanup uses the initiating
// identity and receivers never renew/delete. These fixtures do not qualify physical media.
package org.zmala.phone;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.junit.*;

public class CallPhotoShareTest {
  private File enrollment, photo;
  private CallPhotoFixture http;
  private CallPhotoShare share;
  private ExecutorService worker;
  private final AtomicReference<String> call = new AtomicReference<>("photo-test");
  private final AtomicInteger changes = new AtomicInteger();

  @Before public void create() throws Exception {
    Assume.assumeTrue("Requires a disposable emulator", android.os.Build.MODEL.contains("sdk_gphone"));
    Context context = PhoneFixture.INSTRUMENTATION.getTargetContext();
    enrollment = new File(context.getNoBackupFilesDir(), "enrollment.json");
    photo = new File(context.getCacheDir(), "photo-controller-fixture.jpg");
    Bitmap bitmap = Bitmap.createBitmap(16, 8, Bitmap.Config.ARGB_8888);
    try (OutputStream out = new FileOutputStream(photo)) { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out); }
    finally { bitmap.recycle(); }
    http = new CallPhotoFixture(PhoneFixture.config(9));
    http.photo = Files.readAllBytes(photo.toPath());
    Files.writeString(enrollment.toPath(), PhoneFixture.qr(http.port(), "app"));
    main(() -> share = new CallPhotoShare(context, call::get, changes::incrementAndGet));
    // The production worker lives with CallSession; a per-test controller must release its worker.
    var field = CallPhotoShare.class.getDeclaredField("network"); field.setAccessible(true);
    worker = (ExecutorService) field.get(share);
  }

  @After public void close() throws Exception {
    if (share != null) main(() -> { call.set(null); share.stop(); });
    if (http != null) http.close();
    if (worker != null) { worker.shutdownNow(); assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS)); }
    if (enrollment != null) Files.deleteIfExists(enrollment.toPath());
    if (photo != null) Files.deleteIfExists(photo.toPath());
  }

  private static void main(Runnable work) { PhoneFixture.INSTRUMENTATION.runOnMainSync(work); }
  private static boolean onMain(BooleanSupplier condition) {
    AtomicBoolean result = new AtomicBoolean();
    main(() -> result.set(condition.getAsBoolean()));
    return result.get();
  }
  private void drain() throws Exception {
    worker.submit(() -> {}).get(5, TimeUnit.SECONDS);
    PhoneFixture.INSTRUMENTATION.waitForIdleSync();
  }
  private static void entered(CallPhotoFixture.Gate gate) throws Exception {
    assertTrue("Expected photo request", gate.entered.await(5, TimeUnit.SECONDS));
  }

  @Test public void idlePollsDoNotNotifyTheScreen() throws Exception {
    main(share::reconcile);
    PhoneFixture.waitFor(() -> http.statusRequests >= 3);
    drain();
    assertEquals(0, changes.get());
    assertEquals(0, http.imageRequests);
    assertEquals(0, http.renewals + http.deletes);
  }

  @Test public void unchangedRevisionReusesImageWithoutRepainting() throws Exception {
    http.sharing = true;
    main(share::reconcile);
    PhoneFixture.waitFor(() -> onMain(() -> share.receiving));
    Bitmap[] original = new Bitmap[1]; main(() -> original[0] = share.image);
    PhoneFixture.waitFor(() -> http.statusRequests >= 3);
    drain();
    main(() -> assertSame(original[0], share.image));
    assertEquals(1, changes.get());
    assertEquals(1, http.imageRequests);
    assertEquals(0, http.renewals + http.deletes);
  }

  @Test public void expiredStatusDoesNotDownload() throws Exception {
    http.sharing = true; http.remaining = 0;
    main(share::reconcile);
    PhoneFixture.waitFor(() -> http.statusRequests >= 2);
    drain();
    assertEquals(0, http.imageRequests);
    assertEquals(0, changes.get());
    main(() -> assertNull(share.image));
  }

  @Test public void blockedPollDoesNotExtendOrRestoreTheLease() throws Exception {
    http.sharing = true; http.remaining = 1600;
    main(share::reconcile);
    PhoneFixture.waitFor(() -> onMain(() -> share.receiving));
    var gate = http.statusGate = new CallPhotoFixture.Gate();
    entered(gate);
    PhoneFixture.waitFor(() -> onMain(() -> !share.receiving));
    assertEquals(2, changes.get());
    // The captured response must also be past its own deadline when finally delivered.
    Thread.sleep(1700);
    http.failPhotoRequests = true;
    gate.release.countDown();
    drain();
    main(() -> assertNull(share.image));
    assertEquals(2, changes.get());
    assertEquals(1, http.imageRequests);
    assertEquals(0, http.renewals + http.deletes);
  }

  @Test public void lateImageCannotRestoreAnEndedCall() throws Exception {
    http.sharing = true;
    var gate = http.imageGate = new CallPhotoFixture.Gate();
    main(share::reconcile);
    entered(gate);
    main(() -> { call.set(null); share.reconcile(); });
    gate.release.countDown();
    drain();
    main(() -> { assertNull(share.image); assertFalse(share.receiving); });
    assertEquals(0, changes.get());
    assertEquals(0, http.renewals + http.deletes);
  }

  @Test public void imageArrivingAfterItsLeaseIsDiscarded() throws Exception {
    http.sharing = true; http.remaining = 500;
    var gate = http.imageGate = new CallPhotoFixture.Gate();
    main(share::reconcile);
    entered(gate);
    Thread.sleep(700);
    http.failPhotoRequests = true;
    gate.release.countDown();
    drain();
    main(() -> { assertNull(share.image); assertFalse(share.receiving); });
    assertEquals(0, changes.get());
    assertEquals(1, http.imageRequests);
  }

  @Test public void queuedStopKeepsTheOriginalEnrollment() throws Exception {
    Enrollment original = new Enrollment(PhoneFixture.qr(http.port(), "app"));
    var gate = http.uploadGate = new CallPhotoFixture.Gate();
    main(() -> share.choose(Uri.fromFile(photo), call.get(), original));
    entered(gate);
    try (CallPhotoFixture replacement = new CallPhotoFixture(PhoneFixture.config(9))) {
      Files.writeString(enrollment.toPath(), PhoneFixture.qr(replacement.port(), "app"));
      main(() -> { call.set(null); share.stop(); });
      int afterStop = changes.get();
      gate.release.countDown();
      drain();
      assertEquals(1, http.deletes);
      assertEquals(0, replacement.deletes);
      assertTrue(replacement.authorizations.isEmpty());
      assertTrue(http.authorizations.stream().allMatch(value -> value.equals("Bearer " + PhoneFixture.TOKEN)));
      main(() -> { assertNull(share.image); assertFalse(share.active()); });
      assertEquals(afterStop, changes.get());
    }
  }
}
