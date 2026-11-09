package org.zmala.phone;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.json.JSONObject;

// @llr LLR-AND-CALL-007
/** Session-owned sharing survives Activity recreation. All mutable state belongs to the main thread. */
final class CallPhotoShare {
  private static final long RENEW_MS = 3000;
  private static final long POLL_MS = 1000;
  private static final long MAX_LEASE_MS = 12000;

  private final Context context;
  private final Supplier<String> currentCall;
  private final Runnable changed;
  private final Handler main = new Handler(Looper.getMainLooper());
  // Serialize upload, renewal and deletion so Stop also cleans up an upload still in flight.
  private final ExecutorService network = Executors.newSingleThreadExecutor();
  private final PhotoShareEpoch gate = new PhotoShareEpoch();
  private final Runnable poll = this::poll;
  private final Runnable renew = this::renew;
  private final Runnable expireReceive = this::expireReceivedPhoto;

  private String owner;
  private Enrollment ownerEnrollment;
  private long epoch, revision;
  private String receivingCall;
  private long receiveEpoch, receiveRevision = -1, receiveDeadline;
  boolean busy, receiving;
  Bitmap image;
  int error;

  /** A worker result owns its bitmap until the main thread accepts or discards it. */
  private record ReceivedPhoto(boolean sharing, long revision, long deadline, Bitmap image) {}

  CallPhotoShare(Context context, Supplier<String> currentCall, Runnable changed) {
    this.context = context;
    this.currentCall = currentCall;
    this.changed = changed;
  }

  boolean active() { return owner != null || busy; }

  private boolean current(long ticket, String call) {
    return gate.accepts(ticket, call, currentCall.get());
  }

  void choose(Uri uri, String call, Enrollment enrollment) {
    if (enrollment == null || call == null || !call.equals(currentCall.get()) || receiving) return;
    stop();
    owner = call;
    // Keep the initiating identity even if Forget setup/re-enrollment precedes a queued DELETE.
    ownerEnrollment = enrollment;
    busy = true;
    long ticket = epoch = gate.begin(call);
    changed.run();
    network.execute(() -> upload(uri, call, enrollment, ticket));
  }

  private void upload(Uri uri, String call, Enrollment enrollment, long ticket) {
    try {
      byte[] bytes = CallPhotoImage.read(context.getContentResolver().openInputStream(uri));
      JSONObject result = CallPhotoHttp.request(enrollment, call, "PUT", 0, bytes);
      Bitmap photo = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
      main.post(() -> {
        if (!current(ticket, call)) { discard(photo); return; }
        busy = false;
        image = photo;
        revision = result.optLong("revision");
        changed.run();
        main.postDelayed(renew, RENEW_MS);
      });
    } catch (Exception failure) {
      main.post(() -> failShare(ticket, call));
    }
  }

  private void failShare(long ticket, String call) {
    if (!current(ticket, call)) return;
    stop();
    error = R.string.photo_share_failed;
    changed.run();
  }

  void reconcile() {
    String call = currentCall.get();
    if (owner != null && !owner.equals(call)) stop();
    if (Objects.equals(call, receivingCall)) return;
    receivingCall = call;
    receiveEpoch++;
    receiveRevision = -1;
    receiveDeadline = 0;
    main.removeCallbacks(poll);
    main.removeCallbacks(expireReceive);
    if (receiving) { clearReceivedPhoto(); changed.run(); }
    if (call != null) main.post(poll);
  }

  private void poll() {
    String call = receivingCall;
    long ticket = receiveEpoch;
    if (call == null) return;
    if (active()) { main.postDelayed(poll, POLL_MS); return; }
    // Count queue and network time against the lease, never just time since the response.
    long started = SystemClock.elapsedRealtime();
    long previous = receiveRevision;
    network.execute(() -> {
      ReceivedPhoto result = fetchReceivedPhoto(call, previous, started);
      main.post(() -> applyReceivedPhoto(call, ticket, result));
    });
  }

  private ReceivedPhoto fetchReceivedPhoto(String call, long previous, long started) {
    try {
      Enrollment enrollment = PushAlerts.enrollment(context);
      JSONObject status = CallPhotoHttp.request(enrollment, call, "GET", 0, null);
      long version = status.getLong("revision");
      long deadline = started + Math.min(MAX_LEASE_MS, Math.max(0, status.optLong("remaining_ms")));
      boolean sharing = status.optBoolean("sharing");
      Bitmap photo = null;
      // Unchanged revisions reuse the displayed bitmap; expired status needs no image request.
      if (sharing && version != previous && SystemClock.elapsedRealtime() < deadline) {
        byte[] bytes = CallPhotoHttp.exchange(enrollment, call, "GET", version, null, true);
        photo = CallPhotoImage.decodeReceived(bytes);
      }
      return new ReceivedPhoto(sharing, version, deadline, photo);
    } catch (Exception ignored) {
      // A failed poll preserves the last valid lease, but can never extend it.
      return null;
    }
  }

  private void applyReceivedPhoto(String call, long ticket, ReceivedPhoto result) {
    if (ticket != receiveEpoch || !call.equals(currentCall.get()) || active()) {
      if (result != null) discard(result.image());
      if (ticket == receiveEpoch && call.equals(receivingCall)) main.postDelayed(poll, POLL_MS);
      return;
    }
    Bitmap previous = image;
    boolean wasReceiving = receiving;
    if (result != null) {
      receiveDeadline = result.deadline();
      if (result.sharing() && SystemClock.elapsedRealtime() < receiveDeadline) {
        if (result.image() != null) image = result.image();
        receiving = image != null;
        receiveRevision = receiving ? result.revision() : -1;
      } else {
        discard(result.image());
        clearReceivedPhoto();
      }
    }
    if (SystemClock.elapsedRealtime() >= receiveDeadline) clearReceivedPhoto();
    main.removeCallbacks(expireReceive);
    if (receiving) main.postDelayed(expireReceive, Math.max(0, receiveDeadline - SystemClock.elapsedRealtime()));
    // Lease renewal alone must not refresh the call UI every second.
    if (previous != image || wasReceiving != receiving) changed.run();
    main.postDelayed(poll, POLL_MS);
  }

  private void clearReceivedPhoto() {
    receiving = false;
    image = null;
    receiveRevision = -1;
    receiveDeadline = 0;
  }

  private void expireReceivedPhoto() {
    if (receiving && SystemClock.elapsedRealtime() >= receiveDeadline) {
      clearReceivedPhoto();
      changed.run();
    }
  }

  private static void discard(Bitmap photo) {
    // Only unpublished worker results can be recycled; displayed images may still be in a View.
    if (photo != null) photo.recycle();
  }

  void stop() {
    String previous = owner;
    Enrollment enrollment = ownerEnrollment;
    owner = null;
    ownerEnrollment = null;
    receivingCall = null;
    receiveEpoch++;
    gate.clear();
    busy = false;
    error = 0;
    clearReceivedPhoto();
    main.removeCallbacks(poll);
    main.removeCallbacks(renew);
    main.removeCallbacks(expireReceive);
    changed.run();
    if (previous != null) network.execute(() -> {
      try { CallPhotoHttp.request(enrollment, previous, "DELETE", 0, null); }
      catch (Exception ignored) { /* The sender lease expires without further renewal. */ }
    });
  }

  private void renew() {
    if (image == null || owner == null) return;
    String call = owner;
    Enrollment enrollment = ownerEnrollment;
    long ticket = epoch, version = revision;
    network.execute(() -> {
      boolean alive = false;
      try {
        JSONObject status = CallPhotoHttp.request(enrollment, call, "POST", version, null);
        alive = status.optBoolean("sharing") && status.optLong("revision") == version;
      } catch (Exception ignored) { }
      boolean accepted = alive;
      main.post(() -> {
        if (!current(ticket, call)) return;
        if (accepted) main.postDelayed(renew, RENEW_MS);
        else failShare(ticket, call);
      });
    });
  }
}
