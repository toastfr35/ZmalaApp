// @test A-LLT-031
// @requirements LLR-AND-UI-002
// @environment native-display
// @plan On a disposable emulator, render every notification footer state in the real activity,
// all three photo actions, and setup/update dialogs. Measure the actual clickable views after
// layout, check full text and bounds, and invoke photo/dialog actions. Run at 100% and 200%
// system font scale with English and French app locales. No personal enrollment is used.
// @pass Each app-owned action is at least 60 dp high with its full label inside its bounds;
// noninteractive footer states stay noninteractive, photo callbacks still fire, Cancel leaves setup
// unsaved and Later records deferral. Larger parents do not compensate for undersized clickable children.
package org.zmala.phone;

import static org.junit.Assert.*;
import android.content.Context;
import android.view.View;
import android.widget.TextView;
import androidx.test.rule.ActivityTestRule;
import java.io.File;
import java.nio.file.Files;
import org.junit.*;

public class ActionSizeTest {
  @Rule public ActivityTestRule<MainActivity> activity =
      new ActivityTestRule<>(MainActivity.class, false, false);
  private MainActivity screen;

  @Before public void startEmpty() throws Exception {
    Assume.assumeTrue("Destructive setup scenario requires a disposable emulator",
        android.os.Build.MODEL.contains("sdk_gphone"));
    Context context = PhoneFixture.INSTRUMENTATION.getTargetContext();
    Files.deleteIfExists(new File(context.getNoBackupFilesDir(), "enrollment.json").toPath());
    context.getSharedPreferences("updates", 0).edit().putLong("checked", System.currentTimeMillis())
        .putBoolean("automatic", false).commit();
    screen = activity.launchActivity(null);
    android.os.Bundle arguments = androidx.test.platform.app.InstrumentationRegistry.getArguments();
    if (arguments.containsKey("expectedFontScale")) assertEquals(
        Float.parseFloat(arguments.getString("expectedFontScale")),
        screen.getResources().getConfiguration().fontScale, .01f);
    if (arguments.containsKey("expectedLanguage")) assertEquals(arguments.getString("expectedLanguage"),
        screen.getResources().getConfiguration().getLocales().get(0).getLanguage());
  }

  @After public void close() { activity.finishActivity(); }

  static void assertAction(TextView view) {
    assertNotNull(view);
    assertTrue("Not clickable: " + view.getText(), view.isClickable());
    int minimum = Math.round(60 * view.getResources().getDisplayMetrics().density);
    assertTrue(view.getText() + " height " + view.getHeight() + " < " + minimum,
        view.getHeight() >= minimum);
    assertNotNull(view.getLayout());
    if (view.isAttachedToWindow()) {
      android.graphics.Rect visible = new android.graphics.Rect();
      assertTrue("Hidden action: " + view.getText(), view.getGlobalVisibleRect(visible));
      assertEquals("Clipped action: " + view.getText(), view.getHeight(), visible.height());
      assertEquals("Clipped action width: " + view.getText(), view.getWidth(), visible.width());
    }
    assertTrue("Clipped label: " + view.getText(), view.getLayout().getHeight()
        <= view.getHeight() - view.getCompoundPaddingTop() - view.getCompoundPaddingBottom());
    for (int line = 0; line < view.getLayout().getLineCount(); line++) {
      assertEquals("Ellipsized label: " + view.getText(), 0, view.getLayout().getEllipsisCount(line));
      assertTrue("Label wider than action: " + view.getText(), view.getLayout().getLineWidth(line)
          <= view.getWidth() - view.getCompoundPaddingLeft() - view.getCompoundPaddingRight() + 1);
    }
  }

  @Test public void footerStatesMeetTheMinimumOnTheClickableView() throws Exception {
    var pageField = MainActivity.class.getDeclaredField("page"); pageField.setAccessible(true);
    var footer = MainActivity.class.getDeclaredMethod("footer", AlertStatus.class); footer.setAccessible(true);
    for (AlertStatus state : AlertStatus.values()) {
      PhoneFixture.INSTRUMENTATION.runOnMainSync(() -> {
        try { ((android.view.ViewGroup) pageField.get(screen)).removeAllViews(); footer.invoke(screen, state); }
        catch (Exception error) { throw new AssertionError(error); }
      });
      if (state == AlertStatus.UNAVAILABLE) PhoneFixture.snapshot("action-size-footer");
      PhoneFixture.INSTRUMENTATION.waitForIdleSync();
      PhoneFixture.INSTRUMENTATION.runOnMainSync(() -> {
        int label = switch (state) {
          case SERVER_OFF -> R.string.alerts_server_off;
          case NOTIFICATIONS_OFF -> R.string.notifications_off;
          case PREPARING -> R.string.alerts_preparing;
          case UNAVAILABLE -> R.string.alerts_retry;
          case READY -> R.string.incoming_enabled;
        };
        TextView status = PhoneFixture.text(screen.getWindow().getDecorView(), screen.getString(label));
        assertNotNull(status);
        if (state == AlertStatus.READY || state == AlertStatus.PREPARING) assertFalse(status.isClickable());
        else assertAction(status);
        assertAction(PhoneFixture.text(screen.getWindow().getDecorView(), screen.getString(R.string.updates)));
      });
    }
  }

  @Test public void photoShareCancelAndStopRetainSizeAndActions() {
    PhoneFixture.INSTRUMENTATION.runOnMainSync(() -> {
      int[] clicks = {0};
      CallScreen view = new CallScreen(screen, false, CallEngine.Output.SPEAKER, new CallScreen.Actions() {
        public void mute(boolean value) {} public CallEngine.Output output(CallEngine.Output value) { return value; }
        public int volume(int steps) { return 50; } public void camera() {} public void end() {}
        public void photo() { clicks[0]++; }
      });
      android.graphics.Bitmap photo = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888);
      try {
        for (int state = 0; state < 3; state++) {
          view.photo(true, state == 2 ? photo : null, state == 1);
          view.showControls();
          for (boolean landscape : new boolean[]{false, true}) {
            int width = Math.round((landscape ? 640 : 320) * screen.getResources().getDisplayMetrics().density);
            int height = Math.round((landscape ? 320 : 640) * screen.getResources().getDisplayMetrics().density);
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            view.layout(0, 0, width, height);
            assertAction(view.sharePhoto);
            assertTrue(view.sharePhoto.getLeft() >= 0 && view.sharePhoto.getRight() <= width);
            assertTrue("Photo action overlaps call controls", view.sharePhoto.getBottom() <= view.controls.getTop());
          }
          assertTrue(view.sharePhoto.performClick());
        }
        assertEquals(3, clicks[0]);
      } finally { view.dispose(); photo.recycle(); }
    });
  }

  @Test public void setupConfirmationActionsAreLargeAndCancelDoesNotEnroll() throws Exception {
    PhoneFixture.scan(screen, PhoneFixture.qr(9, "app"));
    checkDialogButton(screen.getString(R.string.connect));
    checkDialogButton(screen.getString(R.string.cancel));
    PhoneFixture.snapshot("action-size-setup");
    PhoneFixture.dialog(screen.getString(R.string.cancel));
    assertFalse(new File(screen.getNoBackupFilesDir(), "enrollment.json").exists());
  }

  @Test public void updateDialogActionsAreLargeAndLaterDefers() throws Exception {
    var enrollment = MainActivity.class.getDeclaredField("enrollment"); enrollment.setAccessible(true);
    var config = MainActivity.class.getDeclaredField("config"); config.setAccessible(true);
    PhoneFixture.INSTRUMENTATION.runOnMainSync(() -> {
      try {
        enrollment.set(screen, new Enrollment(PhoneFixture.qr(9, "app")));
        config.set(screen, PhoneFixture.config(9));
        AppUpdates.get(screen).show();
      } catch (Exception error) { throw new AssertionError(error); }
    });
    checkDialogButton(screen.getString(R.string.update));
    checkDialogButton(screen.getString(R.string.check_now));
    checkDialogButton(screen.getString(R.string.later));
    checkDialogButton(screen.getString(R.string.automatic_updates));
    PhoneFixture.snapshot("action-size-updates");
    PhoneFixture.dialog(screen.getString(R.string.later));
    assertTrue(screen.getSharedPreferences("updates", 0).getLong("later_until", 0) > System.currentTimeMillis());
  }

  private static void checkDialogButton(String text) {
    androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(text))
        .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
        .check((view, missing) -> { if (missing != null) throw missing; assertAction((TextView) view); });
  }
}
