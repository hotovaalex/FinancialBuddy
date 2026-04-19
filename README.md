# Financial Buddy — Android Studio Project

## Project Structure
```
FinancialBuddy/
├── app/
│   ├── src/main/
│   │   ├── java/com/financialbuddy/app/
│   │   │   ├── MainActivity.java                 ← WebView host + JS bridge
│   │   │   ├── PaymentNotificationListener.java  ← Auto-intercepts Google Pay
│   │   │   ├── NotificationPermissionActivity.java
│   │   │   └── BootReceiver.java
│   │   ├── assets/www/
│   │   │   └── index.html                        ← Full web app
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml
│   │   │   └── values/ (strings, themes, colors)
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
└── settings.gradle
```

---

## How to Import into Android Studio

### Step 1 — Open Project
1. Launch **Android Studio** (Electric Eel or newer)
2. Click **"Open"** (NOT "New Project")
3. Navigate to and select the **`FinancialBuddy/`** folder
4. Click **OK** — wait for Gradle sync to complete (~2 min first time)

### Step 2 — Check SDK
- Go to **File → Project Structure → SDK Location**
- Set **Android SDK** to your SDK path (usually `~/Android/Sdk`)
- Set **JDK** to version **17** (bundled with Android Studio)

### Step 3 — Sync Gradle
- If prompted, click **"Sync Now"** in the yellow bar
- Or go to **File → Sync Project with Gradle Files**

### Step 4 — Build APK
**Debug APK (for testing):**
```
Build → Build Bundle(s)/APK(s) → Build APK(s)
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

**Release APK (for distribution):**
```
Build → Generate Signed Bundle/APK → APK
→ Create new keystore (save the password!)
→ Release → V1 + V2 signature → Finish
```
Output: `app/build/outputs/apk/release/app-release.apk`

### Step 5 — Install on Device
```bash
# Via Android Debug Bridge
adb install app/build/outputs/apk/debug/app-debug.apk

# Or drag the APK to your phone via USB
```

---

## Enable Auto-Interception on Device

After installing, do this **once**:

1. Open **Financial Buddy**
2. The app will show a dialog — tap **"Allow"**
3. Android opens **Settings → Notification Access**
4. Find **"Financial Buddy"** in the list
5. **Toggle it ON**
6. Confirm the warning dialog
7. Return to the app — the green dot will appear 🟢

From this point forward:
- Make any Google Pay payment
- Financial Buddy intercepts it **automatically**
- The green banner slides in with amount + merchant
- Tap **"Log It"** → modal opens with everything pre-filled
- **Only action needed: pick a category and tap Save**

---

## How Auto-Interception Works

```
Google Pay fires a notification
          ↓
Android delivers it to ALL NotificationListenerService apps
          ↓
PaymentNotificationListener.onNotificationPosted() called
          ↓
Package name filter: is it Google Pay / a bank?
          ↓
Keyword filter: does text contain "paid", "charged", "debited"?
          ↓
Regex parser extracts: amount + merchant
  Pattern: ([$£€])?\s*(\d+[.,]\d{2}).*?
           (?:at|to|paid to|charge of|debited)\s+([A-Z0-9\s\.]+)
          ↓
LocalBroadcast → MainActivity receives it
          ↓
webView.evaluateJavascript("triggerAutoIntercept(...)") called
          ↓
Green banner slides in + haptic vibration
          ↓
User taps "Log It" → modal opens
  • Amount: pre-filled ✅
  • Merchant: pre-filled ✅  
  • Date: Date.now() automatic ✅
  • Category: user selects
          ↓
Saved to localStorage
```

---

## Supported Payment Apps (auto-detected)

| App | Package |
|-----|---------|
| Google Pay | com.google.android.apps.nbu.paisa.user |
| Google Wallet | com.google.android.apps.walletnfcrel |
| PayPal | com.paypal.android.p2pmobile |
| Venmo | com.venmo |
| Cash App | com.squareup.cash |
| Samsung Pay | com.samsung.android.spay |
| Chase | com.chase.sig.android |
| Bank of America | com.infonow.bofa |
| Wells Fargo | com.wellsfargo.mobile |
| Barclays (UK) | com.barclays.android.barclaysmobilebanking |
| PhonePe (India) | com.phonepe.app |
| Paytm | net.one97.paytm |
| Bank SMS (Google Messages) | com.google.android.apps.messaging |

To add more banks: edit `PAYMENT_PACKAGES` in `PaymentNotificationListener.java`

---

## Customise the Web App

Edit `app/src/main/assets/www/index.html` directly in Android Studio.
- Add budget categories
- Change colours (CSS variables at top of file)
- Modify the regex parser (line with `const re =`)

After editing, rebuild the APK — no server needed, everything is local.

---

## Minimum Requirements
- Android **8.0** (API 26) or higher
- Android Studio **Electric Eel** (2022.1.1) or newer
- JDK **17**
- No internet required — fully offline after font load
