package org.zmala.phone;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.function.Predicate;

// @llr LLR-AND-ENROLL-002, LLR-AND-CALL-006
/** Invalidates work from an old screen and publishes complete enrollments to background readers. */
final class SetupCoordinator {
  private final File file;
  private int generation;
  private boolean fetching;
  SetupCoordinator(File file) { this.file = file; }
  int ticket() { return generation; }
  boolean current(int ticket) { return ticket == generation; }
  void invalidate() { generation++; fetching = false; }
  boolean fetching() { return fetching; }
  void fetching(boolean value) { fetching = value; }
  boolean enrolled() { return file.isFile(); }
  Enrollment load() throws Exception {
    return new Enrollment(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
  }
  private Path stagingFile() {
    return file.toPath().resolveSibling(file.getName() + ".new");
  }
  void remember(Enrollment enrollment) throws IOException {
    Path staging = stagingFile();
    try {
      Files.write(staging, enrollment.serialized().getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
      Files.move(staging, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(staging);
    }
  }
  void forget() throws IOException {
    Files.deleteIfExists(stagingFile());
    Files.deleteIfExists(file.toPath());
    invalidate();
  }
  org.json.JSONObject configuration(Enrollment enrollment) throws Exception {
    return ServerApi.configuration(enrollment);
  }
  static String[] permissions(int sdk) {
    return sdk >= 37 ? new String[]{"android.permission.CAMERA", "android.permission.RECORD_AUDIO", "android.permission.ACCESS_LOCAL_NETWORK"}
        : new String[]{"android.permission.CAMERA", "android.permission.RECORD_AUDIO"};
  }
  static boolean granted(int sdk, Predicate<String> granted) {
    for (String permission : permissions(sdk)) if (!granted.test(permission)) return false;
    return true;
  }
}
