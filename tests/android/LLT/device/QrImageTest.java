// @test A-LLT-020
// @requirements LLR-AND-ENROLL-002
// @environment native-sdk
// @plan Decode QR PNGs and PDFs. Include a cover page and the APK QR before the account QR.
// Supply invalid, empty, non-QR and oversized streams. Inspect closure and scratch cleanup.
// @pass Exact QR text is recovered, PDFs select the App QR and rejected inputs leave no scratch copy.
package org.zmala.phone;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.zxing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class QrImageTest {
  private final File cache = PhoneFixture.INSTRUMENTATION.getTargetContext().getCacheDir();
  static class Input extends ByteArrayInputStream {
    boolean closed;
    Input(byte[] bytes) { super(bytes); }
    @Override public void close() { closed = true; }
  }
  @Test public void localQrImagesAreBoundedAndInputsClose() throws Exception {
    String text = "Zmala setup image fixture";
    com.google.zxing.common.BitMatrix matrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, 512, 512);
    Bitmap bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
    for (int y = 0; y < 512; y++) for (int x = 0; x < 512; x++) bitmap.setPixel(x, y, matrix.get(x, y) ? 0xff000000 : 0xffffffff);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); bitmap.recycle();
    Input good = new Input(out.toByteArray());
    assertEquals(text, QrImage.read(good, cache)); assertTrue(good.closed);
    for (byte[] bytes : new byte[][] {new byte[0], new byte[] {1, 2, 3}, new byte[20 * 1024 * 1024 + 1]}) {
      Input bad = new Input(bytes);
      assertThrows(Exception.class, () -> QrImage.read(bad, cache)); assertTrue(bad.closed);
    }
    Bitmap blank = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
    out.reset(); blank.compress(Bitmap.CompressFormat.PNG, 100, out); blank.recycle();
    Input noQr = new Input(out.toByteArray());
    assertThrows(Exception.class, () -> QrImage.read(noQr, cache)); assertTrue(noQr.closed);
  }

  @Test public void pdfSelectsAppQrAndLeavesNoScratchCopy() throws Exception {
    File scratch = Files.createTempDirectory(cache.toPath(), "pdf-fixture-").toFile();
    try {
      String text = PhoneFixture.qr(12345, "app");
      Input good = new Input(PhoneFixture.setupPdf(text, true));
      assertEquals(text, QrImage.read(good, scratch));
      assertTrue(good.closed);
      assertArrayEquals(new String[0], scratch.list());

      byte[] header = "%PDF-".getBytes(StandardCharsets.US_ASCII);
      byte[] oversized = new byte[20 * 1024 * 1024 + 1];
      System.arraycopy(header, 0, oversized, 0, header.length);
      for (byte[] bytes : new byte[][] {PhoneFixture.setupPdf(null, false), header, oversized}) {
        Input bad = new Input(bytes);
        assertThrows(Exception.class, () -> QrImage.read(bad, scratch));
        assertTrue(bad.closed);
        assertArrayEquals(new String[0], scratch.list());
      }
    } finally { Files.delete(scratch.toPath()); }
  }
}
