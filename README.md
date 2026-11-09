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

`.github/workflows/android-ci.yml` runs on every `push` and `pull_request` and has
two jobs:

1. **build** — sets up JDK 17 + Gradle, runs the existing JVM unit tests and debug
   build, and uploads the debug APK as the `app-debug-apk` artifact:

   ```bash
   ./gradlew testDebugUnitTest assembleDebug --no-daemon --stacktrace
   ```

   Test reports are uploaded as `test-reports` when the job fails. Unit tests are
   never shortened or skipped.

2. **firebase-test-lab** — `needs: build`. It downloads the *same* `app-debug-apk`
   artifact (no rebuild), authenticates keylessly to Google Cloud via GitHub OIDC +
   Workload Identity Federation, and runs a Robo test:

   ```bash
   gcloud firebase test android run \
     --project "$GCP_PROJECT_ID" \
     --app "<downloaded app-debug.apk>" \
     --type robo \
     --device model=MediumPhone.arm,version=35,locale=en,orientation=portrait \
     --timeout 3m \
     --no-auto-google-login
   ```

   A non-zero exit from `gcloud` fails the step, the job, and therefore the whole
   workflow. The gcloud log and (when a results bucket is configured) the raw Test
   Lab results are uploaded as the `firebase-test-lab-results` artifact.

### Firebase Test Lab: first-time setup

The Test Lab job is **gated**: if the two credential secrets are not set it is
skipped with a notice. Once they are set it runs for real (no mock).

Configure these under **Repository → Settings → Secrets and variables → Actions**.

Secrets (`Secrets` tab):

| Name | Value |
| --- | --- |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | `projects/<PROJECT_NUMBER>/locations/global/workloadIdentityPools/<POOL_ID>/providers/<PROVIDER_ID>` |
| `GCP_SERVICE_ACCOUNT` | `<SA_NAME>@<PROJECT_ID>.iam.gserviceaccount.com` |

Variables (`Variables` tab):

| Name | Value |
| --- | --- |
| `GCP_PROJECT_ID` | `ci-loop-smoke` |
| `GCP_RESULTS_BUCKET` | Optional. GCS bucket for raw results (bare name, e.g. `my-results-bucket`; a `gs://` prefix is accepted and stripped). If omitted, gcloud uses its default results bucket and the raw-result download step is skipped. |

The Google Cloud side (WIF pool/provider, service account, IAM bindings, results
bucket) is **external setup done by the operator/Codex**, not by this workflow or
the agent; the workflow only consumes it. Outline:

1. Enable APIs on project `ci-loop-smoke`: Cloud Testing API and Cloud Tool Results
   API (and IAM/STS for Workload Identity Federation).
2. Create a Workload Identity Pool + OIDC provider for GitHub
   (`issuer: https://token.actions.githubusercontent.com`, audience
   `https://iam.googleapis.com/projects/<PROJECT_NUMBER>/locations/global/workloadIdentityPools/<POOL_ID>`),
   restricted to this repository.
3. Create a service account and grant it the Firebase Test Lab role
   (`roles/cloudtestservice.testAdmin` or `roles/firebase.admin`) plus write access
   to the results bucket (e.g. `roles/storage.objectAdmin` on that bucket).
4. Allow the pool's GitHub principal to impersonate the service account
   (`roles/iam.workloadIdentityUser` on the SA for the repo's principal set).

### Viewing Test Lab results / failure logs

- **Workflow log:** Actions run → job **Firebase Test Lab (Robo)** → step
  **Run Robo test in Firebase Test Lab**. The full `gcloud` output is there and in
  `gcloud-testlab.log`.
- **Artifact:** download `firebase-test-lab-results` — it contains the gcloud log
  and, if a results bucket is set, the raw Robo results (e.g. `test_result_*.xml`,
  logcat, video).
- **GCS:** raw results are under `gs://<GCP_RESULTS_BUCKET>/robo-<run_id>-<attempt>/`.
- **Console:** `gcloud` prints a Firebase console link to the matrix run in its
  output.

## Design notes

- **AGP 9 built-in Kotlin** would otherwise add `kotlin-stdlib` to the runtime
  classpath. Because this app is pure Java and references no Kotlin,
  `app/build.gradle` excludes it (`configurations.configureEach { exclude ... }`),
  keeping the APK at ~10 KB with **zero runtime dependencies**.
- No Flutter / Unity / AndroidX / external image resources are used.
- Generated outputs (`build/`, `.gradle/`, APKs, `local.properties`) are ignored by
  `.gitignore`; the debug APK is only a build artifact and is never committed.
- The Firebase Test Lab job uses keyless OIDC (no service-account JSON key) and the
  existing `ci-loop-smoke` cloud project; it adds no Firebase SDK and no
  third-party runtime dependency to the app.
