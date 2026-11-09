package org.zmala.phone;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import java.io.*;
import java.nio.ByteBuffer;

// @llr LLR-AND-CALL-007
/** Downsample before allocating pixels; ImageDecoder applies the source orientation. */
final class CallPhotoImage {
  static final int MAX_BYTES = 2 * 1024 * 1024;
  private static final int MAX_SIDE = 1600;
  private static final int MAX_SOURCE_BYTES = 20 * 1024 * 1024;
  private static final long MAX_SOURCE_PIXELS = 64000000;

  private CallPhotoImage() {}

  static byte[] read(InputStream input) throws Exception {
    if (input == null) throw new IOException("Photo unavailable");
    byte[] bytes;
    try (InputStream in = input) {
      bytes = HttpPolicy.read(in, MAX_SOURCE_BYTES, "Photo too large");
    }
    Bitmap bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes)), (decoder, info, source) -> {
      int w = info.getSize().getWidth(), h = info.getSize().getHeight();
      if (w <= 0 || h <= 0 || (long)w*h > MAX_SOURCE_PIXELS) throw new IllegalArgumentException("Photo too large");
      double scale = Math.min(1, (double)MAX_SIDE/Math.max(w,h));
      decoder.setTargetSize(Math.max(1,(int)(w*scale)), Math.max(1,(int)(h*scale)));
      decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
    });
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) || out.size() > MAX_BYTES)
        throw new IOException("Photo too large");
      return out.toByteArray();
    } finally { bitmap.recycle(); }
  }

  /** Validate the peer's normalized JPEG dimensions before allocating its pixels. */
  static Bitmap decodeReceived(byte[] bytes) throws IOException {
    if (bytes.length < 2 || bytes.length > MAX_BYTES || (bytes[0] & 255) != 255 || (bytes[1] & 255) != 216)
      throw new IOException("Invalid shared photo");
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
    if (bounds.outWidth < 1 || bounds.outHeight < 1 || bounds.outWidth > MAX_SIDE || bounds.outHeight > MAX_SIDE)
      throw new IOException("Invalid shared photo");
    Bitmap photo = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    if (photo == null) throw new IOException("Invalid shared photo");
    return photo;
  }
}
