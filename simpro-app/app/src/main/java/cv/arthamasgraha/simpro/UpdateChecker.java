package cv.arthamasgraha.simpro;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

/**
 * Checks Firebase Remote Config for minimum app version.
 * If current version is lower, shows force update dialog.
 */
public class UpdateChecker {

    private static final String TAG = "UpdateChecker";
    private final Activity activity;
    private final FirebaseRemoteConfig remoteConfig;

    public UpdateChecker(Activity activity) {
        this.activity = activity;
        this.remoteConfig = FirebaseRemoteConfig.getInstance();

        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600) // 1 hour cache
                .build();
        remoteConfig.setConfigSettingsAsync(configSettings);
    }

    public void checkForUpdate() {
        String minVersionKey = activity.getString(R.string.config_remote_config_min_version_key);

        remoteConfig.fetchAndActivate().addOnCompleteListener(activity, task -> {
            if (task.isSuccessful()) {
                String minVersion = remoteConfig.getString(minVersionKey);
                if (!minVersion.isEmpty()) {
                    compareVersions(minVersion);
                }
            } else {
                Log.w(TAG, "Remote config fetch failed", task.getException());
            }
        });
    }

    private void compareVersions(String minVersion) {
        try {
            PackageInfo packageInfo = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0);
            String currentVersion = packageInfo.versionName;

            if (isVersionLower(currentVersion, minVersion)) {
                showUpdateDialog();
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Package not found", e);
        }
    }

    /**
     * Compare two semantic version strings (e.g., "1.0.0" vs "1.1.0")
     */
    private boolean isVersionLower(String current, String minimum) {
        try {
            String[] currentParts = current.split("\\.");
            String[] minParts = minimum.split("\\.");

            int length = Math.max(currentParts.length, minParts.length);
            for (int i = 0; i < length; i++) {
                int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
                int minPart = i < minParts.length ? Integer.parseInt(minParts[i]) : 0;

                if (currentPart < minPart) return true;
                if (currentPart > minPart) return false;
            }
            return false; // versions are equal
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error parsing version", e);
            return false;
        }
    }

    private void showUpdateDialog() {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_dialog_title)
                .setMessage(R.string.update_dialog_message)
                .setCancelable(false)
                .setPositiveButton(R.string.update_dialog_btn, (dialog, which) -> {
                    // Open Play Store
                    String packageName = activity.getPackageName();
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=" + packageName)));
                    } catch (Exception e) {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=" + packageName)));
                    }
                    activity.finish();
                })
                .show();
    }
}
