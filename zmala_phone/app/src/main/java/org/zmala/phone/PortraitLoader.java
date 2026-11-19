package org.zmala.phone;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.File;

// @llr LLR-AND-UI-002
final class PortraitLoader implements AutoCloseable {
  interface Fetch { byte[] read(Enrollment enrollment, String path) throws Exception; }

  private final PortraitQueue local = new PortraitQueue();
  private final PortraitQueue downloads = new PortraitQueue();
  private final Handler main = new Handler(Looper.getMainLooper());
  private final Fetch fetch;
  private final PortraitStore store;
  private final LruCache<String, PortraitStore.Portrait> memory = new LruCache<>(8 * 1024 * 1024) {
    @Override protected int sizeOf(String key, PortraitStore.Portrait portrait) {
      return portrait.bitmap().getAllocationByteCount();
    }
  };

  PortraitLoader(File directory, Fetch fetch) {
    store = new PortraitStore(directory);
    this.fetch = fetch;
  }

  long begin() {
    downloads.begin();
    return local.begin();
  }

  /** Read ahead while the first authenticated directory and call engine are starting. */
  void warm(Enrollment enrollment, long ticket) {
    local.execute(ticket, () -> {
      for (String key : store.keysFor(enrollment)) {
        if (!local.current(ticket)) return;
        if (memory.get(key) != null) continue;
        PortraitStore.Portrait saved = store.read(key);
        synchronized (local) {
          if (local.current(ticket) && saved != null) memory.put(key, saved);
        }
      }
    });
  }

  /** Bind only contacts admitted by the current directory, including incoming callers. */
  void load(ImageView view, Enrollment enrollment, String contact, String path, long ticket) {
    if (enrollment == null || !local.current(ticket) || !contact.matches("[a-z][a-z0-9_-]{0,30}")) return;
    String key = enrollment.storageKey + "." + contact;
    if (path.isEmpty()) {
      memory.remove(key);
      local.execute(ticket, () -> store.remove(key, () -> local.current(ticket)));
      return;
    }
    PortraitStore.Portrait cached = memory.get(key);
    if (cached != null) {
      view.setImageBitmap(cached.bitmap());
      if (!path.equals(cached.path())) download(view, enrollment, key, path, ticket);
      return;
    }
    local.execute(ticket, () -> {
      PortraitStore.Portrait saved = store.read(key);
      if (saved != null) display(view, key, saved, ticket);
      if (saved == null || !path.equals(saved.path())) download(view, enrollment, key, path, ticket);
    });
  }

  private void download(ImageView view, Enrollment enrollment, String key, String path, long ticket) {
    downloads.execute(ticket, () -> {
      try {
        byte[] bytes = fetch.read(enrollment, path);
        if (!downloads.current(ticket)) return;
        Bitmap bitmap = thumbnail(bytes);
        if (bitmap == null) return;
        PortraitStore.Portrait portrait = new PortraitStore.Portrait(path, bitmap);
        store.save(key, portrait, () -> downloads.current(ticket));
        display(view, key, portrait, ticket);
      } catch (Exception ignored) {
        // Keep the last good image. The next bind can retry the new revision.
      }
    });
  }

  private void display(ImageView view, String key, PortraitStore.Portrait portrait, long ticket) {
    main.post(() -> {
      if (local.current(ticket)) {
        memory.put(key, portrait);
        view.setImageBitmap(portrait.bitmap());
      }
    });
  }

  private static Bitmap thumbnail(byte[] bytes) {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    int size = Math.max(options.outWidth, options.outHeight);
    options.inSampleSize = 1;
    while ((long) options.inSampleSize * 256 < size) options.inSampleSize *= 2;
    options.inJustDecodeBounds = false;
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
  }

  void forget() { begin(); memory.evictAll(); store.clear(); }

  @Override public void close() { downloads.close(); local.close(); memory.evictAll(); }
}
