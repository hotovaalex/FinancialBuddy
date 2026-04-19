# GitHub Actions — Setup Guide

## 📋 How to Use This Project on GitHub

---

## STEP 1 — Create a GitHub Repository

1. Go to **[github.com](https://github.com)** → Sign in → Click **"New"**
2. Name it: `financial-buddy`
3. Set to **Private** (recommended — your financial data)
4. Click **"Create repository"**

---

## STEP 2 — Upload the Project Files

### Option A — GitHub Web UI (easiest, no Git needed)
1. On your new repo page, click **"uploading an existing file"**
2. Drag and drop **all files and folders** from the ZIP:
   ```
   FinancialBuddy/
   ├── .github/
   ├── app/
   ├── gradle/
   ├── build.gradle
   ├── settings.gradle
   ├── gradle.properties
   ├── gradlew
   └── gradlew.bat
   ```
3. Scroll down → click **"Commit changes"**

### Option B — Git Command Line
```bash
# Unzip the project
unzip FinancialBuddy_AndroidStudio.zip
cd FinancialBuddy

# Initialize git and push
git init
git add .
git commit -m "Initial commit: Financial Buddy"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/financial-buddy.git
git push -u origin main
```

---

## STEP 3 — Watch Your APK Build

1. Go to your repo on GitHub
2. Click the **"Actions"** tab
3. You'll see **"🚀 Build Financial Buddy APK"** running
4. Click it → wait ~4 minutes for it to complete
5. When done: scroll to the bottom → **Artifacts** section
6. Click **"FinancialBuddy-Debug"** → download ZIP → extract APK

That's it — you have your APK! ✅

---

## STEP 4 — (Optional) Sign Your Release APK

A signed APK is required for Play Store. For personal sideloading, the debug APK works fine.

### 4a — Generate a Keystore (run once on your computer)

**Requires Java installed. Run in terminal/command prompt:**

```bash
keytool -genkeypair \
  -v \
  -keystore financialbuddy.keystore \
  -alias financialbuddy \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

You'll be prompted for:
- **Keystore password** → choose something strong, SAVE IT
- **Key password** → can be same as keystore password, SAVE IT
- **Name, org, city** → can be anything

This creates `financialbuddy.keystore` — **back this file up safely**.

### 4b — Convert Keystore to Base64

```bash
# Mac / Linux
base64 -i financialbuddy.keystore | tr -d '\n'

# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("financialbuddy.keystore"))
```

Copy the long Base64 string output.

### 4c — Add Secrets to GitHub

1. Go to your GitHub repo
2. **Settings → Secrets and variables → Actions → New repository secret**
3. Add these 4 secrets:

| Secret Name        | Value                                    |
|--------------------|------------------------------------------|
| `KEYSTORE_BASE64`  | The Base64 string from step 4b           |
| `KEY_ALIAS`        | `financialbuddy` (or whatever you chose) |
| `KEYSTORE_PASSWORD`| Your keystore password                   |
| `KEY_PASSWORD`     | Your key password                        |

4. Push any change to trigger a new build
5. This time the workflow will produce a **signed** release APK

---

## STEP 5 — Install on Your Android Phone

1. Download the APK from GitHub Actions → Artifacts
2. Transfer to your phone (email, Google Drive, USB, etc.)
3. On your phone: **Settings → Security (or Privacy) → Install unknown apps**
   - Find your browser or file manager → toggle **Allow from this source**
4. Open the APK file → tap **Install**

---

## STEP 6 — Enable Auto-Intercept (Critical!)

After installing the app, do this **one time**:

1. Open **Financial Buddy**
2. A dialog appears — tap **"Allow"**
3. Android opens: **Settings → Notification access**
4. Scroll to find **"Financial Buddy"**
5. Toggle it **ON** → confirm the warning
6. Press back to return to the app
7. The **green dot** 🟢 appears = auto-intercept is live

From now on, every Google Pay payment will **automatically** open the log modal. ✅

---

## Publishing a New Version

```bash
# Make your code changes, then:
git add .
git commit -m "Update: new feature"
git tag v1.0.1
git push && git push --tags
```

This triggers the **release job** and creates a public GitHub Release with the APK attached.

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Build fails: `SDK not found` | The `ubuntu-latest` runner has Android SDK pre-installed — this shouldn't happen. Check the error log. |
| Build fails: `gradlew not found` | Make sure `gradlew` file was uploaded (not just the folder) |
| Build fails: `Java version` | The workflow uses JDK 17 — matches `app/build.gradle` |
| APK installs but no auto-intercept | Make sure Notification Access is enabled (Step 6) |
| Signing fails | Check your 4 secrets are correctly set with no extra spaces |
| `workflow_dispatch` not showing | You must push to `main` or `master` branch first |

---

## File Reference

```
.github/
└── workflows/
    └── build.yml        ← The GitHub Actions workflow (this does the building)

app/
├── build.gradle         ← App dependencies & SDK version
└── src/main/
    ├── AndroidManifest.xml
    ├── assets/www/
    │   └── index.html   ← Your entire web app lives here
    └── java/com/financialbuddy/app/
        ├── MainActivity.java                  ← WebView + JS bridge
        ├── PaymentNotificationListener.java   ← Google Pay interceptor
        ├── BootReceiver.java
        └── NotificationPermissionActivity.java

build.gradle             ← Root Gradle config
settings.gradle          ← Project name
gradle.properties        ← JVM + AndroidX flags
gradlew / gradlew.bat    ← Gradle wrapper scripts
```
