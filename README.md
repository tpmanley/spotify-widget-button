# Audiobook Button

A home-screen widget that plays one specific Spotify audiobook with a single tap.
Open the app (or Share → Audiobook Button from Spotify) to change the book.

## One-time Spotify setup
1. Go to https://developer.spotify.com/dashboard and create an app. Under "Which API/SDKs" choose **Android**.
   - Package name: `com.tpmanley.audiobookbutton`
   - SHA-1: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`
   - Redirect URI: `audiobookbutton://callback`
2. Paste the Client ID into `app/src/main/java/com/tpmanley/audiobookbutton/Config.kt`.

Development-mode Spotify apps require the owner to have Premium.

## Build
The default JDK on this machine (26) is too new for Gradle, so use Android Studio's bundled JDK:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew test assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug logs: `adb logcat -s AudiobookButton`
