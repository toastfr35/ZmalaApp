// @test A-LLT-028
// @requirements LLR-AND-UI-002
// @environment native-sdk
// @plan Reload a generated portrait in a new view generation while another portrait fetch is blocked.
// Load forty distinct bitmaps to exceed the decoded-memory budget and reload the oldest from disk.
// Change enrollment with the same photo path, then retire a view while its fetch is blocked.
// Recreate the loader and block or corrupt an updated image before supplying a valid replacement.
// @pass Previous thumbnails survive relaunch and failed updates. Unchanged revisions need no download.
// Refresh binds the decoded bitmap immediately without another fetch; another enrollment fetches anew;
// an obsolete result never updates its view or fills the cache. Closing rejects later work.
package org.zmala.phone;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class PortraitLoaderTest {
  @org.junit.Rule public final org.junit.rules.TemporaryFolder files = new org.junit.rules.TemporaryFolder(
      PhoneFixture.INSTRUMENTATION.getTargetContext().getCacheDir());

  private void main(Runnable task) { PhoneFixture.INSTRUMENTATION.runOnMainSync(task); }
  private void loaded(ImageView view) throws Exception {
    PhoneFixture.waitFor(() -> {
      boolean[] ready = {false}; main(() -> ready[0] = view.getDrawable() != null); return ready[0];
    });
  }
  @Test public void cacheIsReusedAndEnrollmentIsIsolated() throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, bytes);
    Enrollment first = new Enrollment(PhoneFixture.qr(8080, "app"));
    Enrollment second = new Enrollment(PhoneFixture.qr(8081, "app"));
    CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
    AtomicInteger fetches = new AtomicInteger();
    PortraitLoader[] loader = {null}; ImageView[] view = new ImageView[3];
    main(() -> {
      loader[0] = new PortraitLoader(files.getRoot(), (enrollment, path) -> {
        if (path.equals("/photos/def.png")) {
          started.countDown();
          assertTrue(release.await(5, TimeUnit.SECONDS));
        } else fetches.incrementAndGet();
        return bytes.toByteArray();
      });
      for (int i = 0; i < view.length; i++) view[i] = new ImageView(PhoneFixture.INSTRUMENTATION.getTargetContext());
      loader[0].load(view[0], first, "alice", "/photos/abc.png", loader[0].begin());
    });
    try {
      loaded(view[0]);
      Bitmap[] displayed = new Bitmap[2];
      main(() -> {
        displayed[0] = ((BitmapDrawable) view[0].getDrawable()).getBitmap();
        loader[0].load(new ImageView(view[0].getContext()), first, "bob", "/photos/def.png", loader[0].begin());
      });
      assertTrue(displayed[0].getWidth() <= 256); assertTrue(displayed[0].getHeight() <= 256);
      assertTrue(started.await(5, TimeUnit.SECONDS));
      main(() -> {
        loader[0].load(view[1], first, "alice", "/photos/abc.png", loader[0].begin());
        if (view[1].getDrawable() instanceof BitmapDrawable portrait) displayed[1] = portrait.getBitmap();
      });
      assertSame("Cached portrait must bind while the download is blocked", displayed[0], displayed[1]);
      assertEquals(1, fetches.get());
      release.countDown();
      main(() -> loader[0].load(view[2], second, "alice", "/photos/abc.png", loader[0].begin()));
      loaded(view[2]); assertEquals(2, fetches.get());
    } finally { release.countDown(); main(() -> loader[0].close()); }
  }
  @Test public void obsoleteFetchCannotFillCacheOrChangeView() throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, bytes);
    Enrollment enrollment = new Enrollment(PhoneFixture.qr(8080, "app"));
    CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
    AtomicInteger fetches = new AtomicInteger();
    PortraitLoader[] loader = {null}; ImageView[] view = new ImageView[2];
    main(() -> {
      loader[0] = new PortraitLoader(files.getRoot(), (target, path) -> {
        if (fetches.incrementAndGet() == 1) { started.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); }
        return bytes.toByteArray();
      });
      for (int i = 0; i < view.length; i++) view[i] = new ImageView(PhoneFixture.INSTRUMENTATION.getTargetContext());
      loader[0].load(view[0], enrollment, "alice", "/photos/abc.png", loader[0].begin());
    });
    try {
      assertTrue(started.await(5, TimeUnit.SECONDS));
      main(() -> loader[0].load(view[1], enrollment, "alice", "/photos/abc.png", loader[0].begin()));
      release.countDown(); loaded(view[1]);
      assertEquals(2, fetches.get());
      main(() -> {
        assertNull(view[0].getDrawable()); loader[0].close();
        loader[0].load(view[0], enrollment, "alice", "/photos/abc.png", loader[0].begin());
      });
      assertEquals(2, fetches.get());
    } finally { release.countDown(); main(() -> loader[0].close()); }
  }
  private static byte[] png(int color) {
    Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(color);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes);
    return bytes.toByteArray();
  }

  private ImageView bind(PortraitLoader loader, Enrollment owner, String id, String path, long ticket) {
    ImageView[] view = new ImageView[1];
    main(() -> {
      view[0] = new ImageView(PhoneFixture.INSTRUMENTATION.getTargetContext());
      loader.load(view[0], owner, id, path, ticket);
    });
    return view[0];
  }

  private void shows(ImageView view, int color) throws Exception {
    PhoneFixture.waitFor(() -> {
      boolean[] matches = {false};
      main(() -> matches[0] = view.getDrawable() instanceof BitmapDrawable image
          && image.getBitmap().getPixel(0, 0) == color);
      return matches[0];
    });
  }

  @Test public void restartRetainsLastGoodRevisionUntilReplacementDecodes() throws Exception {
    Enrollment owner = new Enrollment(PhoneFixture.qr(8080, "app"));
    String first = "/photos/abc.png", next = "/photos/def.png";
    int red = android.graphics.Color.RED, blue = android.graphics.Color.BLUE;
    try (PortraitLoader seed = new PortraitLoader(files.getRoot(), (target, path) -> png(red))) {
      shows(bind(seed, owner, "alice", first, seed.begin()), red);
    }

    CountDownLatch blocked = new CountDownLatch(1), release = new CountDownLatch(1);
    AtomicInteger fetches = new AtomicInteger();
    try (PortraitLoader restarted = new PortraitLoader(files.getRoot(), (target, path) -> {
      fetches.incrementAndGet();
      if (path.equals(next)) {
        blocked.countDown();
        if (!release.await(10, TimeUnit.SECONDS)) throw new java.io.IOException("fixture timed out");
        return new byte[] {0};
      }
      return png(blue);
    })) {
      long ticket = restarted.begin();
      ImageView stale = bind(restarted, owner, "alice", next, ticket);
      assertTrue(blocked.await(5, TimeUnit.SECONDS));
      shows(stale, red);
      release.countDown();
      // The following job completes after the malformed replacement has been processed.
      shows(bind(restarted, owner, "bob", first, ticket), blue);
      shows(stale, red);
      assertEquals(2, fetches.get());
    } finally { release.countDown(); }

    CountDownLatch allowUpdate = new CountDownLatch(1);
    fetches.set(0);
    try (PortraitLoader update = new PortraitLoader(files.getRoot(), (target, path) -> {
      fetches.incrementAndGet();
      if (!allowUpdate.await(10, TimeUnit.SECONDS)) throw new java.io.IOException("fixture timed out");
      return png(blue);
    })) {
      long ticket = update.begin();
      ImageView alice = bind(update, owner, "alice", next, ticket);
      shows(alice, red);
      shows(bind(update, owner, "bob", first, ticket), blue);
      allowUpdate.countDown();
      shows(alice, blue);
    } finally { allowUpdate.countDown(); }
    try (PortraitLoader offline = new PortraitLoader(files.getRoot(), (target, path) -> {
      fetches.incrementAndGet(); throw new java.io.IOException("offline");
    })) {
      shows(bind(offline, owner, "alice", next, offline.begin()), blue);
      assertEquals("Only the changed revision was downloaded", 1, fetches.get());
      offline.forget();
      assertEquals(0, files.getRoot().list().length);
    }
  }

  @Test public void warmPortraitBindsOnFirstDrawAndExplicitRemovalClearsIt() throws Exception {
    Enrollment owner = new Enrollment(PhoneFixture.qr(8080, "app"));
    int red = android.graphics.Color.RED;
    String path = "/photos/abc.png";
    try (PortraitLoader seed = new PortraitLoader(files.getRoot(), (target, url) -> png(red))) {
      shows(bind(seed, owner, "alice", path, seed.begin()), red);
    }
    AtomicInteger requests = new AtomicInteger();
    try (PortraitLoader loader = new PortraitLoader(files.getRoot(), (target, url) -> {
      requests.incrementAndGet(); return png(red);
    })) {
      long startup = loader.begin();
      loader.warm(owner, startup);
      // Bind after the warm job has finished on the local worker.
      shows(bind(loader, owner, "bob", path, startup), red);
      long directory = loader.begin();
      main(() -> {
        ImageView row = new ImageView(PhoneFixture.INSTRUMENTATION.getTargetContext());
        loader.load(row, owner, "alice", path, directory);
        assertTrue(row.getDrawable() instanceof BitmapDrawable);
      });
      ImageView removed = bind(loader, owner, "alice", "", directory);
      shows(bind(loader, owner, "charlie", path, directory), red);
      main(() -> assertNull(removed.getDrawable()));
      assertNull(new PortraitStore(files.getRoot()).read(owner.storageKey + ".alice"));
      assertEquals(2, requests.get());
    }
  }

  @Test public void decodedMemoryBudgetEvictsOlderPortraits() throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, bytes);
    Enrollment enrollment = new Enrollment(PhoneFixture.qr(8080, "app"));
    AtomicInteger fetches = new AtomicInteger();
    PortraitLoader[] loader = {null};
    main(() -> loader[0] = new PortraitLoader(files.getRoot(), (target, path) -> { fetches.incrementAndGet(); return bytes.toByteArray(); }));
    try {
      // Forty 480px bitmaps exceed the budget even after any decoder sampling.
      for (int i = 0; i < 40; i++) {
        String path = "/photos/" + Integer.toHexString(i) + ".png";
        String contact = "contact" + i;
        ImageView[] view = {null};
        main(() -> { view[0] = new ImageView(PhoneFixture.INSTRUMENTATION.getTargetContext());
          loader[0].load(view[0], enrollment, contact, path, loader[0].begin()); });
        loaded(view[0]);
      }
      assertEquals(40, fetches.get());
      ImageView[] view = {null};
      main(() -> { view[0] = new ImageView(PhoneFixture.INSTRUMENTATION.getTargetContext());
        loader[0].load(view[0], enrollment, "contact0", "/photos/0.png", loader[0].begin());
        assertNull("Evicted bitmap is loaded asynchronously", view[0].getDrawable()); });
      loaded(view[0]); assertEquals(40, fetches.get());
    } finally { main(() -> loader[0].close()); }
  }
}
