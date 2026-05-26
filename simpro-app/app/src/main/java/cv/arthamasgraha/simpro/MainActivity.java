package cv.arthamasgraha.simpro;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements NetworkReceiver.NetworkListener {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;

    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;
    private View loadingOverlay;
    private View errorLayout;
    private TextView errorTitle;
    private TextView errorMessage;
    private MaterialButton btnRetry;

    private NetworkReceiver networkReceiver;
    private FirebaseAnalytics firebaseAnalytics;
    private UpdateChecker updateChecker;

    private ValueCallback<Uri[]> fileUploadCallback;
    private String cameraPhotoPath;
    private boolean isErrorShowing = false;

    private final ActivityResultLauncher<Intent> fileChooserLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Uri[] results = null;
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String dataString = result.getData().getDataString();
                    if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                } else if (result.getResultCode() == Activity.RESULT_OK && cameraPhotoPath != null) {
                    results = new Uri[]{Uri.parse(cameraPhotoPath)};
                }

                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(results);
                    fileUploadCallback = null;
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initFirebase();
        initWebView();
        initNetworkReceiver();
        requestPermissions();

        // Force update check
        boolean enableForceUpdate = getResources().getBoolean(R.bool.config_enable_force_update);
        if (enableForceUpdate) {
            updateChecker = new UpdateChecker(this);
            updateChecker.checkForUpdate();
        }

        // Load the configured URL
        loadWebUrl();
    }

    private void initViews() {
        webView = findViewById(R.id.webview);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        loadingOverlay = findViewById(R.id.loading_overlay);
        errorLayout = findViewById(R.id.error_layout);
        errorTitle = errorLayout.findViewById(R.id.error_title);
        errorMessage = errorLayout.findViewById(R.id.error_message);
        btnRetry = errorLayout.findViewById(R.id.btn_retry);

        boolean enablePullToRefresh = getResources().getBoolean(R.bool.config_enable_pull_to_refresh);
        swipeRefresh.setEnabled(enablePullToRefresh);
        swipeRefresh.setColorSchemeResources(R.color.colorPrimary, R.color.colorAccent);
        swipeRefresh.setOnRefreshListener(() -> {
            if (isErrorShowing) {
                loadWebUrl();
            } else {
                webView.reload();
            }
        });

        btnRetry.setOnClickListener(v -> loadWebUrl());
    }

    private void initFirebase() {
        boolean enableAnalytics = getResources().getBoolean(R.bool.config_enable_analytics);
        if (enableAnalytics) {
            firebaseAnalytics = FirebaseAnalytics.getInstance(this);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        WebSettings webSettings = webView.getSettings();

        // JavaScript
        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);

        // DOM Storage & Database
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);

        // Cache
        String cacheMode = getString(R.string.config_cache_mode);
        switch (cacheMode) {
            case "LOAD_CACHE_ELSE_NETWORK":
                webSettings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
                break;
            case "LOAD_NO_CACHE":
                webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
                break;
            default:
                webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
                break;
        }

        // Display settings
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setSupportZoom(false);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setDisplayZoomControls(false);

        // Media
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);

        // Mixed content (for Android 5+)
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        // Custom User Agent (disguise WebView)
        String customUA = getString(R.string.config_user_agent);
        webSettings.setUserAgentString(customUA);

        // Geolocation
        boolean enableGeolocation = getResources().getBoolean(R.bool.config_enable_geolocation);
        webSettings.setGeolocationEnabled(enableGeolocation);

        // Disable text selection & long press (to hide WebView feel)
        webView.setLongClickable(false);
        webView.setHapticFeedbackEnabled(false);

        // Cookie persistence (session stays after app close)
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // JS Bridge
        boolean enableJsBridge = getResources().getBoolean(R.bool.config_enable_js_bridge);
        if (enableJsBridge) {
            webView.addJavascriptInterface(new JSBridge(this), "Android");
        }

        // WebViewClient - handles page navigation & errors
        webView.setWebViewClient(new SimproWebViewClient());

        // WebChromeClient - handles file upload, geolocation, permissions
        webView.setWebChromeClient(new SimproWebChromeClient());

        // File download
        boolean enableDownload = getResources().getBoolean(R.bool.config_enable_file_download);
        if (enableDownload) {
            webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
                downloadFile(url, contentDisposition, mimeType);
            });
        }
    }

    private void initNetworkReceiver() {
        networkReceiver = new NetworkReceiver(this);
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkReceiver, filter);
    }

    private void loadWebUrl() {
        String url = getString(R.string.config_web_url);

        if (!NetworkUtil.isNetworkAvailable(this)) {
            showError(
                    getString(R.string.error_no_internet_title),
                    getString(R.string.error_no_internet_message)
            );
            return;
        }

        hideError();
        showLoading();
        webView.loadUrl(url);
    }

    // ===== ERROR HANDLING =====

    private void showError(String title, String message) {
        isErrorShowing = true;
        webView.setVisibility(View.GONE);
        loadingOverlay.setVisibility(View.GONE);
        errorLayout.setVisibility(View.VISIBLE);
        errorTitle.setText(title);
        errorMessage.setText(message);
        swipeRefresh.setRefreshing(false);
    }

    private void hideError() {
        isErrorShowing = false;
        errorLayout.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    private void showLoading() {
        loadingOverlay.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        loadingOverlay.setVisibility(View.GONE);
        swipeRefresh.setRefreshing(false);
    }

    // ===== NETWORK LISTENER =====

    @Override
    public void onNetworkAvailable() {
        runOnUiThread(() -> {
            if (isErrorShowing) {
                loadWebUrl();
            }
        });
    }

    @Override
    public void onNetworkLost() {
        // Optional: show subtle indicator
    }

    // ===== FILE DOWNLOAD =====

    private void downloadFile(String url, String contentDisposition, String mimeType) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);

            request.setTitle(fileName);
            request.setDescription("Mengunduh file...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            // Cookie
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) {
                request.addRequestHeader("Cookie", cookies);
            }
            request.addRequestHeader("User-Agent", webView.getSettings().getUserAgentString());

            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            dm.enqueue(request);

            Toast.makeText(this, "Mengunduh: " + fileName, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Download failed", e);
            Toast.makeText(this, "Gagal mengunduh file", Toast.LENGTH_SHORT).show();
        }
    }

    // ===== PERMISSIONS =====

    private void requestPermissions() {
        List<String> permissions = new ArrayList<>();

        if (getResources().getBoolean(R.bool.config_enable_geolocation)) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (getResources().getBoolean(R.bool.config_enable_camera)) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.CAMERA);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.RECORD_AUDIO);
            }
        }

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Permissions handled silently - WebChromeClient will request again if needed
    }

    // ===== BACK BUTTON =====

    @Override
    public void onBackPressed() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.exit_dialog_title)
                .setMessage(R.string.exit_dialog_message)
                .setPositiveButton(R.string.exit_dialog_yes, (dialog, which) -> {
                    finish();
                })
                .setNegativeButton(R.string.exit_dialog_no, (dialog, which) -> {
                    dialog.dismiss();
                })
                .setCancelable(true)
                .show();
    }

    // ===== LIFECYCLE =====

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkReceiver != null) {
            unregisterReceiver(networkReceiver);
        }
        webView.destroy();
    }

    // ===== INNER CLASSES =====

    /**
     * Custom WebViewClient - handles page loading, errors, and navigation
     */
    private class SimproWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();

            // Handle tel:, mailto:, intent: links externally
            if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("intent:")) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Cannot handle URL: " + url, e);
                }
                return true;
            }

            // All other links stay in webview
            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            showLoading();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            hideLoading();
            hideError();

            // Inject CSS to hide scrollbar and disable text selection for native feel
            String css = "body { -webkit-tap-highlight-color: transparent; } " +
                    "::-webkit-scrollbar { display: none; } " +
                    "* { -webkit-touch-callout: none; }";
            String js = "javascript:(function() { " +
                    "var style = document.createElement('style'); " +
                    "style.innerHTML = '" + css + "'; " +
                    "document.head.appendChild(style); " +
                    "})()";
            view.evaluateJavascript(js, null);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);

            // Only handle main frame errors
            if (request.isForMainFrame()) {
                if (!NetworkUtil.isNetworkAvailable(MainActivity.this)) {
                    showError(
                            getString(R.string.error_no_internet_title),
                            getString(R.string.error_no_internet_message)
                    );
                } else {
                    showError(
                            getString(R.string.error_server_title),
                            getString(R.string.error_server_message)
                    );
                }
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                        android.webkit.WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);

            if (request.isForMainFrame()) {
                int statusCode = errorResponse.getStatusCode();
                if (statusCode == 404) {
                    showError(
                            getString(R.string.error_not_found_title),
                            getString(R.string.error_not_found_message)
                    );
                } else if (statusCode >= 500) {
                    showError(
                            getString(R.string.error_server_title),
                            getString(R.string.error_server_message)
                    );
                }
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            showError(
                    getString(R.string.error_ssl_title),
                    getString(R.string.error_ssl_message)
            );
            handler.cancel();
        }
    }

    /**
     * Custom WebChromeClient - handles file upload, geolocation, media permissions
     */
    private class SimproWebChromeClient extends WebChromeClient {

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                         FileChooserParams fileChooserParams) {
            if (fileUploadCallback != null) {
                fileUploadCallback.onReceiveValue(null);
            }
            fileUploadCallback = filePathCallback;

            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                File photoFile = null;
                try {
                    photoFile = createImageFile();
                } catch (IOException ex) {
                    Log.e(TAG, "Error creating image file", ex);
                }

                if (photoFile != null) {
                    cameraPhotoPath = "file:" + photoFile.getAbsolutePath();
                    Uri photoUri = FileProvider.getUriForFile(MainActivity.this,
                            getPackageName() + ".fileprovider", photoFile);
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                }
            }

            Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
            contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
            contentSelectionIntent.setType("*/*");

            Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
            chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
            chooserIntent.putExtra(Intent.EXTRA_TITLE, "Pilih File");

            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{takePictureIntent});
            }

            fileChooserLauncher.launch(chooserIntent);
            return true;
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin,
                                                       GeolocationPermissions.Callback callback) {
            if (ContextCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                callback.invoke(origin, true, false);
            } else {
                callback.invoke(origin, false, false);
            }
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            runOnUiThread(() -> {
                String[] resources = request.getResources();
                List<String> grantedResources = new ArrayList<>();

                for (String resource : resources) {
                    if (resource.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                        if (ContextCompat.checkSelfPermission(MainActivity.this,
                                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            grantedResources.add(resource);
                        }
                    } else if (resource.equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                        if (ContextCompat.checkSelfPermission(MainActivity.this,
                                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            grantedResources.add(resource);
                        }
                    }
                }

                if (!grantedResources.isEmpty()) {
                    request.grant(grantedResources.toArray(new String[0]));
                } else {
                    request.deny();
                }
            });
        }

        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            Log.d(TAG, "WebConsole: " + consoleMessage.message()
                    + " [line " + consoleMessage.lineNumber() + "]");
            return true;
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "SIMPRO_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }
}
