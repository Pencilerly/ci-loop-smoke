# ci-loop-smoke

Minimal Android **native** game used as a smoke test for a
GitHub Actions → APK → (future) Firebase Test Lab closed loop. It is deliberately
tiny and dependency-free: pure Java, one `Activity`, no AndroidX, no game engine,
no image assets, and **no third-party runtime dependencies**.

| Item | Value |
| --- | --- |
| Package | `com.pencilerly.ciloopsmoke` |
| versionName / versionCode | `1.0.0` / `1` |
| minSdk | 26 |
| compileSdk / targetSdk | 36 (local SDK) |
| Language | Java (no Kotlin sources) |
| Debug APK size | ~10 KB (`app-debug.apk`) |

## Game

A tap-the-target mini game. The screen shows a score and a movable target button.
Tapping the target increments the score by one and repositions the target inside
the playing area. No network, no permissions, no persistence.

## Project layout

```
settings.gradle, build.gradle, gradle.properties, gradlew(.bat), gradle/wrapper/
app/build.gradle
app/src/main/AndroidManifest.xml
app/src/main/java/com/pencilerly/ciloopsmoke/MainActivity.java   # single Activity
app/src/main/java/com/pencilerly/ciloopsmoke/GameState.java      # pure-Java game state
app/src/main/res/layout/activity_main.xml
app/src/main/res/values/strings.xml
app/src/test/java/com/pencilerly/ciloopsmoke/GameStateTest.java  # JVM unit tests
.github/workflows/android-ci.yml
```

- `GameState.java` has **no Android imports**, so its logic is exercised by plain
  JVM unit tests. `GameStateTest` covers the initial score (0) and the score
  increase on tap (plus target-bounds checks).

## Local build & test

Uses the project's own Gradle wrapper (Gradle 9.3.1); no system Gradle needed.
Point the shell at a JDK 17 and an Android SDK that provides `platforms/android-36`
and `build-tools/36.0.0`.

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"

./gradlew testDebugUnitTest assembleDebug
```

On Windows use `gradlew.bat` instead of `./gradlew`.

Debug APK output:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Notes for Windows / non-ASCII project paths

This project can sit under a path with non-ASCII characters. Two things matter on
Windows:

1. **AGP path check.** AGP refuses non-ASCII project paths by default. This is
   already handled by `android.overridePathCheck=true` in `gradle.properties`.
2. **Unit-test worker encoding.** Gradle passes the unit-test classpath to the
   test JVM via a UTF-8 `@argfile`, but the JVM launcher reads that file using the
   OS ANSI code page (e.g. GBK / 936). Non-ASCII path segments get garbled, so
   `testDebugUnitTest` fails with `ClassNotFoundException`. `assembleDebug` is not
   affected. The workaround is to run the build through an **ASCII junction** that
   points at the real project (outputs still land in the real project dir):

   ```bat
   cmd /c mklink /J <ASCII_JUNCTION> "<PROJECT_DIR>"
   cd /d <ASCII_JUNCTION>
   gradlew.bat testDebugUnitTest assembleDebug
   ```

   On an ASCII path (Linux/macOS CI runners, or a plain ASCII Windows path) the
   normal `./gradlew testDebugUnitTest assembleDebug` works with no workaround.

## CI

`.github/workflows/android-ci.yml` runs on every `push` and `pull_request`:

```bash
./gradlew testDebugUnitTest assembleDebug --no-daemon --stacktrace
```

It sets up JDK 17 and Gradle, uploads the debug APK as the `app-debug-apk`
artifact, and uploads test reports when the build fails.

## Design notes

- **AGP 9 built-in Kotlin** would otherwise add `kotlin-stdlib` to the runtime
  classpath. Because this app is pure Java and references no Kotlin,
  `app/build.gradle` excludes it (`configurations.configureEach { exclude ... }`),
  keeping the APK at ~10 KB with **zero runtime dependencies**.
- No Flutter / Unity / AndroidX / external image resources are used.
- Generated outputs (`build/`, `.gradle/`, APKs, `local.properties`) are ignored by
  `.gitignore`; the debug APK is only a build artifact and is never committed.
- This stage performs **only** the GitHub Actions build; Firebase Test Lab and any
  credentials are intentionally out of scope.
