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

    private final BroadcastReceiver paymentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_PAYMENT_DETECTED.equals(intent.getAction())) {
                String amount   = intent.getStringExtra(EXTRA_AMOUNT);
                String merchant = intent.getStringExtra(EXTRA_MERCHANT);
                String source   = intent.getStringExtra(EXTRA_NOTIF_SOURCE);

                Log.d(TAG, "Payment intercepted: " + amount + " at " + merchant);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ((android.os.VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE))
                        .getDefaultVibrator()
                        .vibrate(android.os.VibrationEffect.createWaveform(new long[]{0, 80, 40, 80}, -1));
                }

                injectPaymentToWebView(amount, merchant, source);
            }
        }
    };

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }

        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webview);

        setupWebView();
        loadApp();
        checkNotificationListenerPermission();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
                Log.d(TAG, "JS: " + m.message());
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
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
                view.evaluateJavascript("window.IS_ANDROID_NATIVE = true;", null);
            }
        });
    }

    private void loadApp() {
        webView.loadUrl("file:///android_asset/www/index.html");
    }

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

    private void checkNotificationListenerPermission() {
        Set<String> enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(this);
        if (!enabledListeners.contains(getPackageName())) {
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
        } else {
            webView.post(() -> webView.evaluateJavascript("window.NOTIFICATION_ACCESS_GRANTED = true;", null));
        }
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void requestNotificationAccess() {
            runOnUiThread(() -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        }

        @JavascriptInterface
        public boolean isNotificationAccessGranted() {
            return NotificationManagerCompat.getEnabledListenerPackages(MainActivity.this).contains(getPackageName());
        }

        @JavascriptInterface
        public void showToast(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public void shareText(String text) {
            runOnUiThread(() -> {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(share, "Export Ledger"));
            });
        }

        @JavascriptInterface
        public void vibrate() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.os.VibratorManager vm = (android.os.VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                vm.getDefaultVibrator().vibrate(android.os.VibrationEffect.createWaveform(new long[]{0, 80, 40, 80}, -1));
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(ACTION_PAYMENT_DETECTED);
        LocalBroadcastManager.getInstance(this).registerReceiver(paymentReceiver, filter);
        webView.evaluateJavascript("if(typeof onAppResume==='function') onAppResume();", null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(paymentReceiver);
    }
}
