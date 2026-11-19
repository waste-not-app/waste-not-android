# Android Setup

How to build WasteNot from a fresh checkout, configure backend environments, and sign releases.

## Requirements

- JDK 17.
- Android SDK Platform 36. Point `ANDROID_HOME` at the SDK, or set `sdk.dir` in a `local.properties` file. Git ignores that file.
- An internet connection for the first build, to download dependencies.

The supported baseline is **Android 11 (API 30) and later**. Native, staging, release, and hot reload builds all use this minimum, and CI checks the minimum SDK in the APK manifest with `scripts/verify_minimum_sdk.py`. Compile and target SDK are 36. Primary testing uses the latest stable Android release.

## Build

```sh
./gradlew lintDebug lintStaging lintRelease testDebugUnitTest
./gradlew assembleDebug assembleStaging assembleRelease
python3 scripts/verify_build_environments.py
```

`make check` and `make check-ci` wrap the same steps. Builds are native by default. Hot reload is an opt-in development mode (`-Pwastenot.hotReload=true`); see [DEV_PLUGINS.md](DEV_PLUGINS.md).

### Build types

| Build type | Application ID | Purpose |
|---|---|---|
| `debug` | `com.example.wastenot` | Development; the only type that supports hot reload and a cleartext localhost override |
| `staging` | `com.example.wastenot.staging` | Non-debuggable test build, installed side by side with the others and signed with the debug certificate |
| `release` | `com.example.wastenot` | Distribution build; requires release signing |

The package and application ID remain `com.example.wastenot`. The public identity must be finalized before general distribution.

## Backend configuration

Every build talks to the real backend with real accounts. A fresh install opens sign-in or registration; there are no automatic accounts or local substitute data. Test sources live in `src/testFixtures` and `src/androidTest` and never ship in an APK.

| Build type | Gradle property | Default |
|---|---|---|
| `debug` | `wastenot.debugApiBaseUrl` | `https://waste-not-api.bccdev.id/v1/` |
| `staging` | `wastenot.stagingApiBaseUrl` | Same HTTPS address |
| `release` | `wastenot.releaseApiBaseUrl` | Same HTTPS address |

```sh
# Native builds against the HTTPS backend
./gradlew assembleDebug assembleStaging -Pwastenot.hotReload=false

# Local backend for transport development
./gradlew assembleDebug -Pwastenot.debugApiBaseUrl=http://10.0.2.2:8080/v1/

# Release backend supplied by the environment
./gradlew assembleRelease "-Pwastenot.releaseApiBaseUrl=$WASTENOT_RELEASE_API_BASE_URL"
```

- **URL format.** The base URL must be absolute with a valid host and port, and must not contain user info, a query, or a fragment. Empty values fail the build, and a trailing slash is added if missing.
- **Cleartext.** Cleartext HTTP is allowed only in debug builds, and only for `10.0.2.2`, `localhost`, and `127.0.0.1`. Staging and release builds enforce HTTPS through the [network security configuration](https://developer.android.com/privacy-and-security/security-config).
- **No secrets.** The URL is public configuration exposed as `BuildConfig.API_BASE_URL`. Never store credentials or keys there.
- **Backups.** Cloud backup and device transfer of internal data are disabled. Passwords and refresh tokens must never reach a backup.

## Release signing

`assembleRelease` without signing produces an unsigned APK for build verification only. Signing reads four environment variables from a secret store:

- `ANDROID_KEYSTORE_PATH`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

All four must be set together; a partial configuration is rejected. Never replace the signing certificate without considering update compatibility for installed APKs.

## Devices

Installing, launching, and UI testing need a physical device that the owner has connected and approved. Emulators are not used. When no device is available, record device checks as not run instead of inferring them from host tests.
