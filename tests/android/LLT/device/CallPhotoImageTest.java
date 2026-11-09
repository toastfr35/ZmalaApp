// @test A-LLT-030
// @requirements LLR-AND-CALL-007
// @environment native-display
// @plan Encode synthetic landscape PNG, normalize through Android ImageDecoder, inspect output size/ratio and reject malformed or oversized input streams.
// Validate received JPEG bounds before pixel allocation; cancel a source read and verify stream ownership.
// @pass JPEG output keeps proportions within 1600 pixels and 2 MiB; invalid input cannot produce a photo.
package org.zmala.phone;

import static org.junit.Assert.*;
import android.graphics.*;
import java.io.*;
import org.junit.Test;

public class CallPhotoImageTest {
  @Test public void boundsAndInvalidInput() throws Exception {
    Bitmap source = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888);
    source.eraseColor(Color.BLUE);
    ByteArrayOutputStream input = new ByteArrayOutputStream();
    source.compress(Bitmap.CompressFormat.PNG, 100, input); source.recycle();
    byte[] bytes = CallPhotoImage.read(new ByteArrayInputStream(input.toByteArray()));
    assertTrue(bytes.length <= 2097152);
    assertEquals(0xff, bytes[0] & 255); assertEquals(0xd8, bytes[1] & 255);
    Bitmap result = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    assertEquals(1600, result.getWidth()); assertEquals(800, result.getHeight()); result.recycle();
    assertThrows(Exception.class, () -> CallPhotoImage.read(new ByteArrayInputStream(new byte[30])));
    assertThrows(IOException.class, () -> CallPhotoImage.read(new ByteArrayInputStream(new byte[20*1024*1024+1])));
  }

  @Test public void receivedImagesMustBeBoundedJpeg() throws Exception {
    Bitmap source = Bitmap.createBitmap(1601, 8, Bitmap.Config.ARGB_8888);
    ByteArrayOutputStream large = new ByteArrayOutputStream();
    source.compress(Bitmap.CompressFormat.JPEG, 85, large);
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    source.compress(Bitmap.CompressFormat.PNG, 100, png);
    source.recycle();
    assertThrows(IOException.class, () -> CallPhotoImage.decodeReceived(large.toByteArray()));
    assertThrows(IOException.class, () -> CallPhotoImage.decodeReceived(png.toByteArray()));
    assertThrows(IOException.class, () -> CallPhotoImage.decodeReceived(new byte[]{(byte)255, (byte)216}));
    assertThrows(IOException.class, () -> CallPhotoImage.decodeReceived(new byte[2 * 1024 * 1024 + 1]));
    byte[] normalized = CallPhotoImage.read(new ByteArrayInputStream(png.toByteArray()));
    Bitmap decoded = CallPhotoImage.decodeReceived(normalized);
    try { assertEquals(1600, decoded.getWidth()); }
    finally { decoded.recycle(); }
  }

  @Test public void cancelledSourceReadClosesTheStream() {
    boolean[] closed = {false};
    InputStream input = new ByteArrayInputStream(new byte[10]) {
      @Override public void close() { closed[0] = true; }
    };
    Thread.currentThread().interrupt();
    try {
      assertThrows(InterruptedIOException.class, () -> CallPhotoImage.read(input));
      assertTrue(Thread.currentThread().isInterrupted());
      assertTrue(closed[0]);
    } finally { Thread.interrupted(); }
  }
}
