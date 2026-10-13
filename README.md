# WasteNot Android

WasteNot membantu pengguna mencatat aktivitas Reduce, Reuse, Recycle dan Trash, mencari panduan, serta memantau kebiasaan pribadi.

## Status implementasi

Starter saat ini memiliki satu module `:app`, Navigation 3, theme awal dan Beranda dengan data contoh. Autentikasi, HTTP client, pencatatan, statistik dan AI masih mengikuti backlog implementasi. Mockup HTML adalah referensi desain dan simulasi.

- [Roadmap dan seluruh tugas](https://github.com/waste-not-app/waste-not-android/issues/2)
- [Kanban iterasi berjalan](https://github.com/orgs/waste-not-app/projects/1/views/1)
- [Setup Android dan konfigurasi lingkungan](docs/ANDROID_SETUP.md)

## Toolchain

Kotlin 2.3.20, AGP 9.0.1, Gradle 9.1.0, JDK 17, Compose BOM 2026.03.01, Material 3 dan Navigation 3. Min SDK 24; compile/target SDK 36. Versi tersimpan di [version catalog](gradle/libs.versions.toml).

## Build dan pemeriksaan

Siapkan JDK 17, Android SDK Platform 36, serta `ANDROID_HOME` atau `sdk.dir` dalam `local.properties` lokal.

```sh
./gradlew lintDebug lintStaging lintRelease testDebugUnitTest
./gradlew assembleDebug assembleStaging assembleRelease
python3 scripts/verify_build_environments.py
python3 scripts/verify_release_workflow.py
```

APK tersedia di `app/build/outputs/apk/{debug,staging,release}/`. Release tanpa konfigurasi signing merupakan APK unsigned. Staging/release belum memiliki alamat backend default; URL HTTPS disediakan sebagai property Gradle sesuai [panduan setup](docs/ANDROID_SETUP.md).

## Alur kontribusi

Buat branch tugas `codex/<ticket>-<nama>` dari commit dasar yang sudah diverifikasi. Remote saat audit memiliki `main`; checkout terpisah dapat dipakai untuk menjaga perubahan lokal lainnya. Commit hanya file terkait tugas setelah pemeriksaan lulus, lalu ajukan PR untuk review. Setiap perubahan kode harus menyebut issue terkait; merge ke main menjalankan pipeline rilis yang sudah ada.

[Android CI](.github/workflows/android-ci.yml) menjalankan lint, unit test, pemeriksaan konfigurasi dan build tiga lingkungan pada branch pengembangan, termasuk `codex/**`. Workflow [release/deploy](.github/workflows/android-release.yml) terpisah dan membutuhkan konfigurasi signing serta secrets deployment. Panduannya ada di [CICD_SETUP.md](docs/CICD_SETUP.md).

## Referensi

| Dokumen | Isi |
|---|---|
| [Design kit terbaru](<docs/WasteNot-Product-Design-Kit update/WasteNot/README.md>) | Indeks PRD, arsitektur, API, desain dan mockup S01–S25 |
| [PRD](<docs/WasteNot-Product-Design-Kit update/WasteNot/PRD.md>) | Scope, aturan unit/metrik dan acceptance |
| [Arsitektur](<docs/WasteNot-Product-Design-Kit update/WasteNot/ARCHITECTURE.md>) | State, repository, navigasi dan infrastruktur |
| [Design system](<docs/WasteNot-Product-Design-Kit update/WasteNot/design-system.html>) | Referensi visual interaktif dengan data simulasi |
| [AI specification](<docs/WasteNot-Product-Design-Kit update/WasteNot/AI_SPEC.md>) | Capture, konfirmasi, rekomendasi dan manual fallback |
| [Developer plugins](docs/DEV_PLUGINS.md) | HotSwan, KSP dan navigation graph |
| [AGENT.md](AGENT.md), [CLAUDE.md](CLAUDE.md) | Petunjuk implementasi untuk agent |

Perintah tambahan tersedia melalui `make help`. Tooling plugin yang sudah ada dipertahankan; backend/API nyata dan pembagian pekerjaan mengikuti roadmap.
