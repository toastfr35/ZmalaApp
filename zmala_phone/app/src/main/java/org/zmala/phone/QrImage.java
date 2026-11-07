package org.zmala.phone;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;
import java.io.*;
import java.nio.file.Files;
import java.util.Map;

// @llr LLR-AND-ENROLL-002
/** Decode a bounded local image or PDF without retaining or uploading the document. */
final class QrImage {
  private static final int MAX_BYTES = 20 * 1024 * 1024;
  private static final int MAX_SIDE = 1800;
  private static final int MAX_PDF_PAGES = 10;
  private static final Map<DecodeHintType, ?> QR_HINTS = Map.of(
      DecodeHintType.POSSIBLE_FORMATS, java.util.List.of(BarcodeFormat.QR_CODE),
      DecodeHintType.TRY_HARDER, true);

  static String read(InputStream input, File cacheDir) throws Exception {
    if (input == null) throw new IOException("Could not open this document.");
    try (BufferedInputStream in = new BufferedInputStream(input)) {
      in.mark(5);
      boolean pdf = in.read() == '%' && in.read() == 'P' && in.read() == 'D'
          && in.read() == 'F' && in.read() == '-';
      in.reset();
      if (pdf) return readPdf(in, cacheDir);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      copy(in, out);
      return readImage(out.toByteArray());
    }
  }

  private static void copy(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[8192];
    int size = 0;
    for (int count; (count = in.read(buffer)) != -1; ) {
      size += count;
      if (size > MAX_BYTES) throw new IOException("Document is too large.");
      out.write(buffer, 0, count);
    }
  }

  private static String readImage(byte[] bytes) throws Exception {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    if (options.outWidth <= 0 || options.outHeight <= 0
        || (long) options.outWidth * options.outHeight > 64000000)
      throw new IOException("Choose a picture containing your setup QR code.");
    options.inSampleSize = 1;
    while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > MAX_SIDE) options.inSampleSize *= 2;
    options.inJustDecodeBounds = false;
    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    if (bitmap == null) throw new IOException("Could not read this image.");
    try {
      String text = new MultiFormatReader().decode(binary(bitmap), QR_HINTS).getText();
      if (text.length() > 8192) throw new IOException("QR code is too large.");
      return text;
    } finally { bitmap.recycle(); }
  }

  private static String readPdf(InputStream in, File cacheDir) throws Exception {
    File scratch = File.createTempFile("setup-qr-", ".pdf", cacheDir);
    try (ParcelFileDescriptor source = ParcelFileDescriptor.open(scratch, ParcelFileDescriptor.MODE_READ_WRITE)) {
      // PdfRenderer needs a seekable file, but the picker can supply a pipe. Unlink
      // before writing so even process termination leaves no document in the cache.
      Files.delete(scratch.toPath());
      try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(source.dup())) {
        copy(in, out);
      }
      try (PdfRenderer pdf = new PdfRenderer(source)) {
        int pages = Math.min(pdf.getPageCount(), MAX_PDF_PAGES);
        for (int i = 0; i < pages; i++) {
          try (PdfRenderer.Page page = pdf.openPage(i)) {
            String text = readPage(page);
            if (text != null) return text;
          }
        }
      }
    } finally { Files.deleteIfExists(scratch.toPath()); }
    throw new IOException("No App setup QR code found.");
  }

  private static String readPage(PdfRenderer.Page page) {
    int width = page.getWidth(), height = page.getHeight();
    if (width <= 0 || height <= 0) return null;
    double scale = (double) MAX_SIDE / Math.max(width, height);
    Bitmap bitmap = Bitmap.createBitmap(Math.max(1, (int) Math.round(width * scale)),
        Math.max(1, (int) Math.round(height * scale)), Bitmap.Config.ARGB_8888);
    try {
      bitmap.eraseColor(Color.WHITE);
      page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
      for (Result result : new QRCodeMultiReader().decodeMultiple(binary(bitmap), QR_HINTS)) {
        String text = result.getText();
        try {
          new Enrollment(text);
          return text;
        } catch (Exception ignored) {
          // The server setup sheet also contains an APK download QR code.
        }
      }
    } catch (NotFoundException ignored) {
      // A document may have a cover page without QR codes.
    } finally { bitmap.recycle(); }
    return null;
  }

  private static BinaryBitmap binary(Bitmap bitmap) {
    int width = bitmap.getWidth(), height = bitmap.getHeight();
    int[] pixels = new int[width * height];
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
    return new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
  }
}
