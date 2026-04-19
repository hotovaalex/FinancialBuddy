package com.financialbuddy.app;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "FinancialBuddy";
    public  static final String ACTION_PAYMENT_DETECTED = "com.financialbuddy.PAYMENT_DETECTED";
    public  static final String EXTRA_NOTIF_TEXT        = "notif_text";
    public  static final String EXTRA_NOTIF_SOURCE      = "notif_source";
    public  static final String EXTRA_AMOUNT            = "amount";
    public  static final String EXTRA_MERCHANT          = "merchant";

    private WebView webView;

    // ── BroadcastReceiver — listens for parsed payment events from NotificationListenerService ──
    private final BroadcastReceiver paymentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_PAYMENT_DETECTED.equals(intent.getAction())) {
                String text     = intent.getStringExtra(EXTRA_NOTIF_TEXT);
                String source   = intent.getStringExtra(EXTRA_NOTIF_SOURCE);
                String amount   = intent.getStringExtra(EXTRA_AMOUNT);
                String merchant = intent.getStringExtra(EXTRA_MERCHANT);

                Log.d(TAG, "Payment intercepted: " + amount + " at " + merchant);

                // Vibrate
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ((android.os.VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE))
                        .getDefaultVibrator()
                        .vibrate(android.os.VibrationEffect.createWaveform(new long[]{0, 80, 40, 80}, -1));
                }

                // Call into JavaScript — triggers auto-intercept modal
                injectPaymentToWebView(amount, merchant, source);
            }
        }
    };

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full-screen, edge-to-edge
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }

        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webview);

        setupWebView();
        loadApp();
        checkNotificationListenerPermission();
        handleSharedIntent(getIntent()); // Handle share from other apps
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);          // localStorage support
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Enable WebView debugging in debug builds
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);

        // Attach JavaScript bridge
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
                Log.d(TAG, "JS: " + m.message() + " [" + m.sourceId() + ":" + m.lineNumber() + "]");
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Open external links in browser
                String url = request.getUrl().toString();
                if (!url.startsWith("file://") && !url.startsWith("about:")) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Tell the web app we're running inside Android native
                view.evaluateJavascript("window.IS_ANDROID_NATIVE = true;", null);
                Log.d(TAG, "App loaded: " + url);
            }
        });
    }

    private void loadApp() {
        // Load from assets/www/index.html
        webView.loadUrl("file:///android_asset/www/index.html");
    }

    // ── Handle text shared from other apps (bank SMS) ──
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleSharedIntent(intent);
    }

    private void handleSharedIntent(Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_SEND.equals(intent.getAction()) &&
                "text/plain".equals(intent.getType())) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null && !sharedText.isEmpty()) {
                Log.d(TAG, "Shared text received: " + sharedText);
                // Pass to JS parser
                String escaped = sharedText.replace("'", "\\'").replace("\n", " ");
                webView.post(() ->
                    webView.evaluateJavascript(
                        "if(typeof handleSharedText === 'function') handleSharedText('" + escaped + "');",
                        null));
            }
        }
    }

    // ── Inject payment data into WebView JS ──
    private void injectPaymentToWebView(String amount, String merchant, String source) {
        String js = String.format(
            "if(typeof triggerAutoIntercept === 'function') { " +
            "  triggerAutoIntercept({amount: %s, merchant: '%s'}, '%s'); " +
            "}",
            amount,
            merchant.replace("'", "\\'"),
            source.replace("'", "\\'")
        );
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    // ── Check & prompt for Notification Listener permission ──
    private void checkNotificationListenerPermission() {
        Set<String> enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(this);
        if (!enabledListeners.contains(getPackageName())) {
            showNotificationAccessDialog();
        } else {
            // Already granted — tell the WebView
            webView.post(() ->
                webView.evaluateJavascript("window.NOTIFICATION_ACCESS_GRANTED = true;", null));
        }
    }

    private void showNotificationAccessDialog() {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
            .setTitle("Enable Payment Auto-Detection")
            .setMessage("Financial Buddy needs Notification Access to automatically detect Google Pay and bank transactions.\n\nTap 'Allow' → find 'Financial Buddy' → enable the toggle.")
            .setPositiveButton("Allow", (d, w) -> {
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                startActivity(intent);
            })
            .setNegativeButton("Later", (d, w) -> d.dismiss())
            .setCancelable(false)
            .show();
    }

    // ── JavaScript Bridge ─────────────────────────────────────────────────────
    public class AndroidBridge {

        /** Called from JS to request notification access */
        @JavascriptInterface
        public void requestNotificationAccess() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                startActivity(intent);
            });
        }

        /** Called from JS to check if notification access is granted */
        @JavascriptInterface
        public boolean isNotificationAccessGranted() {
            Set<String> enabled = NotificationManagerCompat.getEnabledListenerPackages(MainActivity.this);
            return enabled.contains(getPackageName());
        }

        /** Called from JS to show native Android toast */
        @JavascriptInterface
        public void showToast(String message) {
            runOnUiThread(() ->
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
        }

        /** Called from JS to trigger Android share sheet */
        @JavascriptInterface
        public void shareText(String text) {
            runOnUiThread(() -> {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(share, "Export Ledger"));
            });
        }

        /** Called from JS to vibrate device */
        @JavascriptInterface
        public void vibrate() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.os.VibratorManager vm =
                    (android.os.VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                vm.getDefaultVibrator().vibrate(
                    android.os.VibrationEffect.createWaveform(new long[]{0, 80, 40, 80}, -1));
            }
        }

        /** Called from JS — simulate a notification (for testing in-app) */
        @JavascriptInterface
        public void simulatePaymentNotification(String text) {
            String[] parsed = PaymentNotificationListener.parsePaymentText(text);
            if (parsed != null) {
                injectPaymentToWebView(parsed[0], parsed[1], "Google Pay (Simulated)");
            }
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────
    @Override
    protected void onResume() {
        super.onResume();
        // Register receiver for payment events from NotificationListenerService
        IntentFilter filter = new IntentFilter(ACTION_PAYMENT_DETECTED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LocalBroadcastManager.getInstance(this).registerReceiver(paymentReceiver, filter);
        } else {
            LocalBroadcastManager.getInstance(this).registerReceiver(paymentReceiver, filter);
        }
        // Notify JS that app is in foreground (triggers clipboard check)
        webView.evaluateJavascript("if(typeof onAppResume==='function') onAppResume();", null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(paymentReceiver);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
