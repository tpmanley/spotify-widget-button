# AGENTS.md

Notes for coding agents working on Audiobook Button. See `README.md` for what the app does and how users set it up.

## Build and test
```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"  # system JDK 26 is too new for Gradle 9.3
./gradlew test assembleDebug
```
- The project is a single module, `:app`, written in Kotlin. minSdk is 26 and compileSdk/targetSdk are 36. AGP is 8.13 and Gradle 9.3.1, with versions kept in `gradle/libs.versions.toml`.
- Unit tests are JVM-only, in `app/src/test`. Keep pure logic (link parsing, JSON parsing) in plain objects so it stays testable. `org.json` is a test dependency because `android.jar` only has stubs.
- Spotify App Remote isn't on Maven, so it's vendored as `app/libs/spotify-app-remote-release-0.8.0.aar`. It needs `gson`. The auth library comes from Maven: `com.spotify.android:auth`.

## Testing on a device
- There's usually **no adb connection** to the owner's phone. The way changes get tested is: build the APK, send the file to the user, they install it, tap the widget, and paste back the **Last widget tap** log from the app.
- When you add or change behaviour in `PlayActivity`, record each meaningful step with `step("…")`. That log is the main debugging tool, so make steps say *why* a decision was made, not just what happened.
- Nothing involving Spotify can be checked in an emulator. Say clearly what was only built and unit-tested and what was confirmed on the phone.

## Architecture
| File | Role |
|---|---|
| `AudiobookWidgetProvider` | `RemoteViews` widget with one toggle button: Play opens `PlayActivity`; Stop sends a broadcast to `StopReceiver` |
| `PlayActivity` | Invisible single-task trampoline. Flow: check other devices (Web API) → maybe ask "Play here?" → App Remote connect → `connectSwitchToLocalDevice()` → resume or play → finish. Falls back to opening the book page on failure |
| `WebApi` / `SpotifyToken` | `GET /v1/me/player` and device classification (`isThisPhone`). The token comes from the auth library's TOKEN flow, lasts about 1h and can't be refreshed; it's cached in prefs |
| `MediaWatcherService` | `NotificationListenerService`, used only to watch Spotify's `MediaSession` and Android's audio playback state, so the widget switches between Play and Stop |
| `PlaybackStatus` | Decides "playing on this phone" (see the pitfalls below), finds Spotify's `MediaController` |
| `StopReceiver` | Pauses Spotify through its media session |
| `MainActivity` | Compose edit screen: link/title, Share-sheet intake, notification-access setup, last-tap log |
| `BookStore`, `SpotifyLink`, `OEmbed` | Prefs storage, link → `spotify:<type>:<id>` parsing, title lookup |

## Pitfalls we hit
These are confirmed on a real device (Oct 2026). Don't "simplify" them away.
- **`showAuthView(true)` makes `SpotifyAppRemote.connect()` hang forever** with no callback and no prompt. Keep `showAuthView(false)`, the 6s connect watchdog, and the explicit `AuthorizationClient.openLoginActivity` approval.
- **App Remote plays on Spotify's *active* device**, which may be a speaker. Always call `connectApi.connectSwitchToLocalDevice()` before `play()`/`resume()`.
- **Spotify's media session reports PLAYING and LOCAL playback type even during Spotify Connect playback.** "Playing on this phone" therefore also requires `AudioManager.isMusicActive`.
- **The Spotify-app auth flow ignores custom params**, so PKCE isn't possible through it. That's why the Web API token is short-lived.
- **Adding OAuth scopes triggers a new approval prompt**, so change `PlayActivity.SCOPES` sparingly.
- **`AUTHENTICATION_SERVICE_UNAVAILABLE` means the Spotify dashboard doesn't match** the package name, SHA-1 or redirect URI. It's not a code bug.
- **Activities that must survive another app coming to the front can't use `noHistory`.** That attribute broke the "open Spotify and retry" path.
- **Audiobooks may be saved as `spotify:show:` URIs.** Playback tries the alternate form (`SpotifyLink.alternateUri`), and the "already playing" check compares IDs, not full URIs.

## Conventions
- Keep it small and dependency-light. The widget uses plain `RemoteViews`, not Glance.
- A failed "other device" check must never block playback. Just continue.
- User-facing strings go in `res/values/strings.xml`.
