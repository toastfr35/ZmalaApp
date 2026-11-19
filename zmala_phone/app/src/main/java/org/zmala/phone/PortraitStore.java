package org.zmala.phone;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.BooleanSupplier;

// @llr LLR-AND-UI-002
/** One complete thumbnail and its server revision per enrolled contact. */
final class PortraitStore {
  record Portrait(String path, Bitmap bitmap) {}
  private static final int MAX_ENTRIES = 64;
  private final File directory;

  PortraitStore(File directory) { this.directory = directory; }

  String[] keysFor(Enrollment enrollment) {
    String prefix = enrollment.storageKey + ".";
    String[] keys = directory.list((dir, name) -> name.startsWith(prefix));
    return keys == null ? new String[0] : keys;
  }

  Portrait read(String key) {
    File file = new File(directory, key);
    try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
      String path = input.readUTF();
      Bitmap bitmap = BitmapFactory.decodeStream(input);
      return bitmap == null ? null : new Portrait(path, bitmap);
    } catch (IOException ignored) {
      return null;
    }
  }

  void save(String key, Portrait portrait, BooleanSupplier current) {
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    if (!portrait.bitmap().compress(Bitmap.CompressFormat.PNG, 100, png)) return;
    synchronized (PortraitStore.class) {
      if (!current.getAsBoolean() || (!directory.isDirectory() && !directory.mkdirs())) return;
      File pending = new File(directory, ".pending");
      try {
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(pending)))) {
          output.writeUTF(portrait.path());
          png.writeTo(output);
        }
        Files.move(pending.toPath(), new File(directory, key).toPath(), StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
        File[] files = directory.listFiles();
        if (files != null && files.length > MAX_ENTRIES) {
          Arrays.sort(files, Comparator.comparingLong(File::lastModified));
          for (int i = 0; i < files.length - MAX_ENTRIES; i++) files[i].delete();
        }
      } catch (IOException ignored) {
        // A full cache volume can still display the freshly downloaded bitmap.
      } finally {
        pending.delete();
      }
    }
  }

  void remove(String key, BooleanSupplier current) {
    synchronized (PortraitStore.class) {
      if (current.getAsBoolean()) new File(directory, key).delete();
    }
  }

  void clear() {
    synchronized (PortraitStore.class) {
      File[] files = directory.listFiles();
      if (files != null) for (File file : files) file.delete();
    }
  }
}
