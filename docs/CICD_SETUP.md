# CI/CD and APK Distribution

## Environments

Android CI and the `Android Release & Deploy` workflow run on the self-hosted `alberd-staging-android` runner. The backend, AI workers, and landing site use their own runners on the same staging host.

| Resource | URL |
|---|---|
| Landing site | <https://waste-not.bccdev.id/> |
| Release page | <https://waste-not.bccdev.id/apk/> |
| Release metadata | <https://waste-not.bccdev.id/apk/versions.json> |
| Latest APK | <https://waste-not.bccdev.id/apk/latest.apk> |
| Specific version | `https://waste-not.bccdev.id/apk/wastenot-v<VERSION>.apk` |
| Android API | <https://waste-not-api.bccdev.id/v1/> |

## Workflows

**Android CI** runs on pushes to `main`, feature branches, and pull requests. It performs these steps:

1. Lints all three build types.
2. Runs the debug unit tests.
3. Checks the URL configuration.
4. Builds the debug, staging, and release APKs.
5. Uploads the reports as artifacts.

CI never uses signing or deployment credentials.

**Android Release & Deploy** runs only on `v*` tags or a manual dispatch:

1. Runs lint and the unit tests.
2. Builds the native APK.
3. Verifies that the APK uses the remote adapter.
4. Copies the signed APK straight to the runner host's filesystem. There is no SSH or SCP step.

A deployment is refused if the APK is not signed with the release keystore.

### Configuration

| Name | Type | Purpose |
|---|---|---|
| `WASTENOT_RELEASE_API_BASE_URL` | Repository variable | HTTPS API origin, `https://waste-not-api.bccdev.id/v1/`. An empty value stops the release |
| `ANDROID_KEYSTORE_BASE64` | Secret | Release keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Secret | Keystore password |
| `ANDROID_KEY_ALIAS` | Secret | Key alias |
| `ANDROID_KEY_PASSWORD` | Secret | Key password |
| `BACKEND_DIR` | Secret (optional) | Publication directory, `/var/www/wastenot-apk` by default. Nginx must serve the same directory |

A manual version input is treated as an environment value and validated as a safe file name.

## Filesystem and Nginx

`scripts/manage_releases.py <BACKEND_DIR>` manages releases in `<BACKEND_DIR>`:

- reads `<BACKEND_DIR>/apk/`;
- keeps the three newest versions;
- syncs `apk/latest.apk`;
- generates `index.html` and `versions.json` in the parent directory.

`/etc/nginx/sites-available/waste-not` maps:

| URL | Path |
|---|---|
| `/` | `/var/www/wastenot-landing/` |
| `/apk/` | `/var/www/wastenot-apk/index.html` |
| `/apk/versions.json` | `/var/www/wastenot-apk/versions.json` |
| `/apk/<file>.apk` | `/var/www/wastenot-apk/apk/<file>.apk` |

- **Metadata.** Served with `Cache-Control: no-store`.
- **APKs.** Served as `application/vnd.android.package-archive` with `Content-Disposition: attachment`.
- **Public URLs.** The `apk/` subfolder on disk does not appear in the public URL.

## Verification

`python3 scripts/verify_release_workflow.py` runs the build, packaging, and deploy steps in a temporary directory. It checks that the metadata, the release page links, and the versioned and latest files all agree. Test fixtures are never published.

After a release:

```sh
curl --fail https://waste-not.bccdev.id/apk/versions.json
curl --fail --head https://waste-not.bccdev.id/apk/latest.apk
curl --fail https://waste-not-api.bccdev.id/health/ready
```

Then confirm three things:

1. The downloaded APK's size and SHA-256 match the file on the host.
2. The landing page shows the version information.
3. The download button fetches the APK rather than the SPA fallback page.

HTTP checks do not prove that the app works on a device. Physical device checks run separately at the owner's request.
