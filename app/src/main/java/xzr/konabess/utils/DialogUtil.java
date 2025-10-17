package xzr.konabess.utils;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.annotation.StringRes;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import xzr.konabess.R;

import java.util.concurrent.CountDownLatch;

/**
 * Modern Material You Dialog Utility
 * Menggunakan Material Components untuk dialog dengan desain flat modern
 */
public class DialogUtil {
    
    /**
     * Menampilkan error dialog dengan Material You design
     */
    public static void showError(Activity activity, String text) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.error)
                .setMessage(text)
                .setCancelable(false)
                .setPositiveButton(R.string.ok, (dialog, which) -> activity.finish())
                .show();
    }

    public static void showError(Activity activity, int text_res) {
        showError(activity, activity.getResources().getString(text_res));
    }

    /**
     * Menampilkan error dialog dengan detail yang bisa di-copy
     */
    public static void showDetailedError(Activity activity, String err, String detail) {
        err += "\n" + activity.getResources().getString(R.string.long_press_to_copy);
        
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setPadding(48, 24, 48, 0);
        
        TextView textView = new TextView(activity);
        textView.setTextIsSelectable(true);
        textView.setText(detail);
        textView.setTextSize(14);
        
        scrollView.addView(textView);
        
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.error)
                .setMessage(err)
                .setOnDismissListener(dialog -> activity.finish())
                .setView(scrollView)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    public static void showDetailedError(Activity activity, int err, String detail) {
        showDetailedError(activity, activity.getResources().getString(err), detail);
    }

    /**
     * Menampilkan info dialog dengan detail
     */
    public static void showDetailedInfo(Activity activity, String title, String what,
                                        String detail) {
        what += "\n" + activity.getResources().getString(R.string.long_press_to_copy);
        
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setPadding(48, 24, 48, 0);
        
        TextView textView = new TextView(activity);
        textView.setTextIsSelectable(true);
        textView.setText(detail);
        textView.setTextSize(14);
        
        scrollView.addView(textView);
        
        new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setMessage(what)
                .setView(scrollView)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    public static void showDetailedInfo(Activity activity, int title, int what, String detail) {
        showDetailedInfo(activity, activity.getResources().getString(title),
                activity.getResources().getString(what), detail);
    }

    /**
     * Membuat progress dialog modern dengan Material You
     */
    public static AlertDialog getWaitDialog(Context context, int id) {
        return getWaitDialog(context, context.getResources().getString(id));
    }

    public static AlertDialog getWaitDialog(Context context, String text) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setPadding(48, 32, 48, 32);
        container.setGravity(android.view.Gravity.CENTER_VERTICAL);
        
        ProgressBar progressBar = new ProgressBar(context);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        progressParams.setMarginEnd(32);
        progressBar.setLayoutParams(progressParams);
        
        TextView textView = new TextView(context);
        textView.setText(text);
        textView.setTextSize(16);
        
        container.addView(progressBar);
        container.addView(textView);

        return new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.please_wait)
                .setCancelable(false)
                .setView(container)
                .create();
    }

    public static ProgressDialogController createProgressDialog(Activity activity) {
        return new ProgressDialogController(activity);
    }

    public static class ProgressDialogController {
        private final Activity activity;
        private AlertDialog dialog;
        private TextView messageView;

        private ProgressDialogController(Activity activity) {
            this.activity = activity;
            initializeDialog();
        }

        private void initializeDialog() {
            if (!isActivityUsable()) {
                return;
            }

            CountDownLatch latch = new CountDownLatch(1);
            activity.runOnUiThread(() -> {
                try {
                    if (!isActivityUsable()) {
                        return;
                    }

                    LinearLayout container = new LinearLayout(activity);
                    container.setOrientation(LinearLayout.HORIZONTAL);
                    container.setPadding(48, 32, 48, 32);
                    container.setGravity(android.view.Gravity.CENTER_VERTICAL);

                    ProgressBar progressBar = new ProgressBar(activity);
                    LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    progressParams.setMarginEnd(32);
                    progressBar.setLayoutParams(progressParams);

                    messageView = new TextView(activity);
                    messageView.setTextSize(16);

                    container.addView(progressBar);
                    container.addView(messageView);

                    dialog = new MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.please_wait)
                            .setCancelable(false)
                            .setView(container)
                            .create();
                } finally {
                    latch.countDown();
                }
            });

            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        public void show(@StringRes int messageRes) {
            show(activity.getString(messageRes));
        }

        public void show(String message) {
            if (!isActivityUsable() || dialog == null || messageView == null) {
                return;
            }
            if (!isActivityUsable()) {
                return;
            }
            activity.runOnUiThread(() -> {
                if (!isActivityUsable() || dialog == null || messageView == null) {
                    return;
                }
                messageView.setText(message);
                if (!dialog.isShowing()) {
                    dialog.show();
                }
            });
        }

        public void updateMessage(@StringRes int messageRes) {
            updateMessage(activity.getString(messageRes));
        }

        public void updateMessage(String message) {
            if (!isActivityUsable() || messageView == null) {
                return;
            }
            activity.runOnUiThread(() -> messageView.setText(message));
        }

        public void dismiss() {
            if (!isActivityUsable() || dialog == null) {
                return;
            }
            activity.runOnUiThread(() -> {
                if (!isActivityUsable() || dialog == null) {
                    return;
                }
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
            });
        }

        private boolean isActivityUsable() {
            if (activity == null) {
                return false;
            }
            if (activity.isFinishing()) {
                return false;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) {
                return false;
            }
            return true;
        }
    }

    /**
     * Confirmation dialog dengan Material You
     */
    public static void showConfirmation(Context context, String title, String message,
                                       DialogInterface.OnClickListener positiveListener) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.confirm, positiveListener)
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public static void showConfirmation(Context context, int title, int message,
                                       DialogInterface.OnClickListener positiveListener) {
        showConfirmation(context,
                context.getResources().getString(title),
                context.getResources().getString(message),
                positiveListener);
    }

    /**
     * Simple info dialog
     */
    public static void showInfo(Context context, String title, String message) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    public static void showInfo(Context context, int title, int message) {
        showInfo(context,
                context.getResources().getString(title),
                context.getResources().getString(message));
    }

    /**
     * Dialog builder untuk custom dialog
     */
    public static MaterialAlertDialogBuilder createDialogBuilder(Context context) {
        return new MaterialAlertDialogBuilder(context);
    }
}
