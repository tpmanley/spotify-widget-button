# Audiobook Button

An Android home-screen widget that plays one specific Spotify audiobook with a single tap.

- **Play:** tap the widget. It moves playback to your phone and resumes the book where you left off.
- **Stop:** while Spotify is playing on your phone, the widget's green Play button turns into a red Stop button.
- **Someone else is listening:** if your account is already playing on another device (a speaker, a computer, another phone), the tap asks first: *"Playing on Living Room… Play here / Cancel"*.
- **Change the book:** in Spotify, Share the audiobook → **Audiobook Button**, or open the app and paste a link. The title fills in automatically.

## Requirements
- Android 8.0+ with the Spotify app installed and logged in
- Spotify Premium (needed for remote playback control and for development-mode Spotify apps)

## One-time Spotify setup
1. At https://developer.spotify.com/dashboard, create an app. Under the APIs/SDKs used, tick **Android** and **Web API**.
   - Android package name: `com.tpmanley.audiobookbutton`
   - SHA-1 of the signing key. For debug builds, get it with:
     `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`
   - Redirect URI: `audiobookbutton://callback`
   - Click **Save**. If your account isn't the owner, add it under **User Management**.
2. Put the Client ID in `app/src/main/java/com/tpmanley/audiobookbutton/Config.kt`.

## Build and install
The machine's default JDK is too new for Gradle, so build with Android Studio's bundled JDK:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew test assembleDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`. You can install it two ways:
- `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- Copy it to the phone and open it there. Android will ask you to allow installs from that app.

## First run on the phone
1. Choose the book: Share it from Spotify to Audiobook Button and tap **Save**.
2. Add the widget: long-press the home screen → Widgets → Audiobook Button.
3. Tap the widget and approve the Spotify permission prompt. This happens once, and again if the requested permissions change.
4. To get the Stop button, open the app and tap **Allow notification access**.
   - If the switch is greyed out as a "Restricted setting", go to App info → ⋮ → **Allow restricted settings**, then try again. Android does this for apps installed from a file.

## Troubleshooting
Open the app. The **Last widget tap** section shows a timestamped log of every step of the most recent tap: connecting, approval, the "playing elsewhere" check, switching to the phone, and play. You can also see it with `adb logcat -s AudiobookButton`.

| Symptom | Likely cause |
|---|---|
| `AUTHENTICATION_SERVICE_UNAVAILABLE` | The package name, SHA-1 or redirect URI in the Spotify dashboard doesn't match the app |
| The tap only opens Spotify | Playback failed and the app fell back to opening the book page; the log says why |
| The tap flashes Spotify briefly | The access token is renewed about once an hour; this is normal |
