package com.financialbuddy.app;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PaymentNotificationListener
 *
 * Android NotificationListenerService that automatically intercepts
 * Google Pay, banking, and payment app notifications.
 *
 * Flow:
 *   1. User grants Notification Access in Android Settings
 *   2. This service receives ALL notifications silently
 *   3. Filters to known payment package names
 *   4. Parses amount + merchant using the same regex as the web app
 *   5. Broadcasts result → MainActivity → WebView JS → auto-raises modal
 *
 * NO user action required after initial setup.
 */
public class PaymentNotificationListener extends NotificationListenerService {

    private static final String TAG = "PaymentListener";

    // ── Regex from spec — matches currency + amount + merchant ──
    // /([\$£€])?\s*(\d+[\.,]\d{2}).*?(?:at|to|from|purchased|charge of|paid to|debit of|debited)\s+([A-Z0-9\s\.\*\-&]+)/i
    private static final Pattern PAYMENT_REGEX = Pattern.compile(
        "([\\$£€])?\\s*(\\d+[.,]\\d{2}).*?" +
        "(?:at|to|from|purchased|charge of|paid to|debit of|debited|payment to|sent to)\\s+" +
        "([A-Z0-9\\s\\.\\*\\-&]{2,40})",
        Pattern.CASE_INSENSITIVE
    );

    // ── Package names of apps whose notifications we intercept ──
    private static final Set<String> PAYMENT_PACKAGES = new HashSet<>(Arrays.asList(
        // Google Pay
        "com.google.android.apps.nbu.paisa.user",
        "com.google.android.apps.walletnfcrel",
        "com.android.vending",           // Play Store purchases
        // PayPal
        "com.paypal.android.p2pmobile",
        // Venmo
        "com.venmo",
        // Cash App
        "com.squareup.cash",
        // Samsung Pay
        "com.samsung.android.spay",
        // Banks — US
        "com.chase.sig.android",
        "com.infonow.bofa",
        "com.citibank.mobile.au",
        "com.usaa.mobile.android.usaa",
        "com.wellsfargo.mobile",
        "com.capitalone.mobile",
        "com.americanexpress.android.acctsvcs.us",
        // Banks — UK
        "com.barclays.android.barclaysmobilebanking",
        "com.tescobank.mobile",
        "com.lloydsbank.mobile",
        "uk.co.hsbc.hsbcukmobilebanking",
        // Banks — International
        "au.com.cba.netbank",
        "com.anz.android",
        "com.standardchartered.mobile.android",
        // India
        "com.phonepe.app",
        "net.one97.paytm",
        "in.org.npci.upiapp",
        // Generic SMS apps (bank SMS alerts)
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "org.thoughtcrime.securesms"       // Signal (some banks use)
    ));

    // ── Keywords that confirm a notification is a payment alert ──
    private static final String[] PAYMENT_KEYWORDS = {
        "paid", "payment", "charged", "debited", "transaction",
        "purchase", "spent", "sent", "transferred", "debit",
        "google pay", "gpay", "receipt", "approved", "authorized"
    };

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        String packageName = sbn.getPackageName();

        // ── Filter 1: Is this from a known payment app? ──
        // Also check SMS apps since banks send SMS alerts
        boolean isPaymentApp = PAYMENT_PACKAGES.contains(packageName);
        boolean isSmsApp     = packageName.contains("messaging") ||
                               packageName.contains("sms") ||
                               packageName.contains("message");

        if (!isPaymentApp && !isSmsApp) return;

        // ── Extract notification text ──
        Bundle extras = sbn.getNotification().extras;
        if (extras == null) return;

        String title = extras.getString(Notification.EXTRA_TITLE, "");
        String text  = extras.getString(Notification.EXTRA_TEXT,  "");
        String big   = extras.getString(Notification.EXTRA_BIG_TEXT, "");

        // Use the longest available text for best regex match
        String fullText = big.length() > text.length() ? big : text;
        if (fullText.isEmpty()) fullText = title + " " + text;

        Log.d(TAG, "Notification from " + packageName + ": " + fullText);

        // ── Filter 2: Does it contain payment keywords? ──
        if (!containsPaymentKeyword(fullText.toLowerCase())) {
            Log.d(TAG, "No payment keywords found, skipping");
            return;
        }

        // ── Filter 3: Parse amount + merchant ──
        String[] parsed = parsePaymentText(fullText);
        if (parsed == null) {
            Log.d(TAG, "Regex did not match: " + fullText);
            return;
        }

        String amount   = parsed[0];
        String merchant = parsed[1];

        Log.d(TAG, "✅ Payment detected! Amount: " + amount + " | Merchant: " + merchant);

        // ── Broadcast to MainActivity → WebView ──
        String source = resolveSourceLabel(packageName);
        broadcastPayment(fullText, amount, merchant, source);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Not needed
    }

    // ── Parse notification text using the spec regex ──
    public static String[] parsePaymentText(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher m = PAYMENT_REGEX.matcher(text);
        if (!m.find()) return null;

        String currency = m.group(1) != null ? m.group(1) : "$";
        String amount   = m.group(2).replace(",", ".");
        String merchant = m.group(3).trim().replaceAll("\\s+", " ");

        // Sanitise merchant — remove trailing noise
        merchant = merchant.replaceAll("(?i)\\s*(ltd|llc|inc|plc|corp)\\.*\\s*$", "").trim();

        return new String[]{ amount, merchant, currency };
    }

    // ── Check for payment keywords ──
    private boolean containsPaymentKeyword(String text) {
        for (String kw : PAYMENT_KEYWORDS) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    // ── Map package name to human-readable label ──
    private String resolveSourceLabel(String pkg) {
        if (pkg.contains("google") || pkg.contains("nbu") || pkg.contains("wallet")) return "Google Pay";
        if (pkg.contains("paypal"))  return "PayPal";
        if (pkg.contains("venmo"))   return "Venmo";
        if (pkg.contains("cash"))    return "Cash App";
        if (pkg.contains("samsung")) return "Samsung Pay";
        if (pkg.contains("phonepe")) return "PhonePe";
        if (pkg.contains("paytm"))   return "Paytm";
        if (pkg.contains("messaging") || pkg.contains("sms")) return "Bank SMS";
        return "Payment Alert";
    }

    // ── Send LocalBroadcast to MainActivity ──
    private void broadcastPayment(String rawText, String amount, String merchant, String source) {
        Intent intent = new Intent(MainActivity.ACTION_PAYMENT_DETECTED);
        intent.putExtra(MainActivity.EXTRA_NOTIF_TEXT,   rawText);
        intent.putExtra(MainActivity.EXTRA_AMOUNT,       amount);
        intent.putExtra(MainActivity.EXTRA_MERCHANT,     merchant);
        intent.putExtra(MainActivity.EXTRA_NOTIF_SOURCE, source);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}
