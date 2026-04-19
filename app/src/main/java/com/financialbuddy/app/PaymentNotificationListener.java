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

public class PaymentNotificationListener extends NotificationListenerService {

    private static final String TAG = "PaymentListener";

    // ── Regex 1: Standard bank alerts (e.g. "You paid $20.00 to Starbucks") ──
    private static final Pattern STANDARD_REGEX = Pattern.compile(
        "([\\$£€])?\\s*(\\d+[.,]\\d{2}).*?" +
        "(?:at|to|from|purchased|charge of|paid to|debit of|debited|payment to|sent to)\\s+" +
        "([A-Z0-9\\s\\.\\*\\-&]{2,40})",
        Pattern.CASE_INSENSITIVE
    );

    // ── Regex 2: Google Wallet (VALUE ONLY) ──
    // Simply finds the currency symbol and the numbers (e.g., "€20.00") and ignores the rest.
    private static final Pattern WALLET_REGEX = Pattern.compile(
        "([\\$£€])?\\s*(\\d+[.,]\\d{2})",
        Pattern.CASE_INSENSITIVE
    );

    // Known payment apps and generic SMS apps
    private static final Set<String> PAYMENT_PACKAGES = new HashSet<>(Arrays.asList(
        "com.google.android.apps.nbu.paisa.user",
        "com.google.android.apps.walletnfcrel", // Google Wallet
        "com.android.vending",
        "com.paypal.android.p2pmobile",
        "com.venmo",
        "com.squareup.cash",
        "com.samsung.android.spay",
        "com.chase.sig.android",
        "com.infonow.bofa",
        "com.citibank.mobile.au",
        "com.usaa.mobile.android.usaa",
        "com.wellsfargo.mobile",
        "com.capitalone.mobile",
        "com.americanexpress.android.acctsvcs.us",
        "com.barclays.android.barclaysmobilebanking",
        "com.tescobank.mobile",
        "com.lloydsbank.mobile",
        "uk.co.hsbc.hsbcukmobilebanking",
        "au.com.cba.netbank",
        "com.anz.android",
        "com.standardchartered.mobile.android",
        "com.phonepe.app",
        "net.one97.paytm",
        "in.org.npci.upiapp",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "org.thoughtcrime.securesms"
    ));

    private static final String[] PAYMENT_KEYWORDS = {
        "paid", "payment", "charged", "debited", "transaction",
        "purchase", "spent", "sent", "transferred", "debit",
        "google pay", "gpay", "receipt", "approved", "authorized"
    };

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        String packageName = sbn.getPackageName();
        boolean isPaymentApp = PAYMENT_PACKAGES.contains(packageName);
        boolean isSmsApp = packageName.contains("messaging") || packageName.contains("sms") || packageName.contains("message");

        if (!isPaymentApp && !isSmsApp) return;

        Bundle extras = sbn.getNotification().extras;
        if (extras == null) return;

        String title = extras.getString(Notification.EXTRA_TITLE, "");
        String text  = extras.getString(Notification.EXTRA_TEXT,  "");
        String big   = extras.getString(Notification.EXTRA_BIG_TEXT, "");

        String fullText = big != null && big.length() > text.length() ? big : text;
        if (fullText == null) fullText = "";
        if (title == null) title = "";

        Log.d(TAG, "Notification from " + packageName + " | Title: " + title + " | Text: " + fullText);

        String amount = null;
        String merchant = null;

        // ── 1. Check Google Wallet (Value-only fetch) ──
        if (packageName.contains("wallet")) {
            Matcher walletMatcher = WALLET_REGEX.matcher(fullText.trim());
            if (walletMatcher.find()) {
                amount = walletMatcher.group(2).replace(",", ".");
                // We map the Title to the Merchant so the UI field has something to display
                merchant = title.trim(); 
            }
        }
        // ── 2. Check Standard Bank / GPay format ──
        else {
            if (!containsPaymentKeyword(fullText.toLowerCase() + " " + title.toLowerCase())) {
                return;
            }

            String combinedText = title + " " + fullText;
            Matcher stdMatcher = STANDARD_REGEX.matcher(combinedText);
            
            if (stdMatcher.find()) {
                amount = stdMatcher.group(2).replace(",", ".");
                merchant = stdMatcher.group(3).trim().replaceAll("\\s+", " ");
                merchant = merchant.replaceAll("(?i)\\s*(ltd|llc|inc|plc|corp)\\.*\\s*$", "").trim();
            } else {
                Matcher textMatcher = STANDARD_REGEX.matcher(fullText);
                if (textMatcher.find()) {
                    amount = textMatcher.group(2).replace(",", ".");
                    merchant = textMatcher.group(3).trim().replaceAll("\\s+", " ");
                    merchant = merchant.replaceAll("(?i)\\s*(ltd|llc|inc|plc|corp)\\.*\\s*$", "").trim();
                }
            }
        }

        if (amount != null && merchant != null) {
            Log.d(TAG, "✅ Payment detected! Amount: " + amount + " | Merchant: " + merchant);
            String source = resolveSourceLabel(packageName);
            broadcastPayment(fullText, amount, merchant, source);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
    }

    private boolean containsPaymentKeyword(String text) {
        for (String kw : PAYMENT_KEYWORDS) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private String resolveSourceLabel(String pkg) {
        if (pkg.contains("google") || pkg.contains("nbu") || pkg.contains("wallet")) return "Google Wallet";
        if (pkg.contains("paypal"))  return "PayPal";
        if (pkg.contains("venmo"))   return "Venmo";
        if (pkg.contains("cash"))    return "Cash App";
        if (pkg.contains("samsung")) return "Samsung Pay";
        if (pkg.contains("phonepe")) return "PhonePe";
        if (pkg.contains("paytm"))   return "Paytm";
        if (pkg.contains("messaging") || pkg.contains("sms")) return "Bank SMS";
        return "Payment Alert";
    }

    private void broadcastPayment(String rawText, String amount, String merchant, String source) {
        Intent intent = new Intent(MainActivity.ACTION_PAYMENT_DETECTED);
        intent.putExtra(MainActivity.EXTRA_NOTIF_TEXT,   rawText);
        intent.putExtra(MainActivity.EXTRA_AMOUNT,       amount);
        intent.putExtra(MainActivity.EXTRA_MERCHANT,     merchant);
        intent.putExtra(MainActivity.EXTRA_NOTIF_SOURCE, source);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}
