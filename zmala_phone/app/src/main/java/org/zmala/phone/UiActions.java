package org.zmala.phone;

import android.app.AlertDialog;
import android.widget.Button;
import android.widget.TextView;

// @llr LLR-AND-UI-002
/** Size the clickable view itself, including the framework buttons in our dialogs. */
final class UiActions {
  private UiActions() {}

  static void size(TextView action) {
    int minimum = Math.round(60 * action.getResources().getDisplayMetrics().density);
    action.setMinHeight(Math.max(minimum, action.getMinHeight()));
    action.setMinimumHeight(Math.max(minimum, action.getMinimumHeight()));
    action.setSingleLine(false);
    action.setMaxLines(Integer.MAX_VALUE);
    action.setEllipsize(null);
  }

  static AlertDialog show(AlertDialog dialog) {
    dialog.show();
    for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
      Button button = dialog.getButton(which);
      if (button != null) size(button);
    }
    return dialog;
  }
}
