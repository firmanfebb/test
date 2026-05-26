package cv.arthamasgraha.simpro;

import android.content.Context;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

/**
 * JavaScript Bridge - allows JavaScript in WebView to call native Android functions.
 * 
 * Usage from JavaScript:
 *   Android.showToast("Hello from web!");
 *   Android.getDeviceInfo();
 *   Android.getAppVersion();
 */
public class JSBridge {

    private final Context context;

    public JSBridge(Context context) {
        this.context = context;
    }

    /**
     * Show a native Android Toast message
     */
    @JavascriptInterface
    public void showToast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Get device information
     */
    @JavascriptInterface
    public String getDeviceInfo() {
        return "{" +
                "\"brand\":\"" + android.os.Build.BRAND + "\"," +
                "\"model\":\"" + android.os.Build.MODEL + "\"," +
                "\"sdk\":\"" + android.os.Build.VERSION.SDK_INT + "\"," +
                "\"version\":\"" + android.os.Build.VERSION.RELEASE + "\"" +
                "}";
    }

    /**
     * Get app version name
     */
    @JavascriptInterface
    public String getAppVersion() {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Get app package name
     */
    @JavascriptInterface
    public String getPackageName() {
        return context.getPackageName();
    }

    /**
     * Check if network is available
     */
    @JavascriptInterface
    public boolean isNetworkAvailable() {
        return NetworkUtil.isNetworkAvailable(context);
    }
}
