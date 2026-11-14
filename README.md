# WasteNot Android

Aplikasi Android untuk mencatat aktivitas Reduce, Reuse, Recycle dan Trash, membaca panduan 3R, melihat statistik pribadi, serta meninjau deteksi dan saran penanganan dari foto.

## Status implementasi

Seluruh 20 screen wajib dan empat dialog sudah tersedia. Penyelarasan desain, navigasi dan pemeriksaan state masih berjalan; kelengkapan screen bukan penerimaan akhir rilis. Lihat [prioritas UI dan checkpoint](docs/UI_FIRST_PLAN.md).

Semua varian aplikasi menggunakan akun dan layanan backend nyata. Instalasi baru membuka Masuk/Daftar; autentikasi tidak pernah dibuat lokal. Catatan, statistik, progres, panduan dan deteksi/rekomendasi diperoleh melalui API Go. Foto hanya dikirim setelah pengguna mengonfirmasi, dan saran tidak menyimpan aktivitas tanpa tinjauan serta input jumlah. Refresh token disimpan melalui Android Keystore.

- [Kontrak integrasi Android](https://github.com/waste-not-app/waste-not-backend/blob/main/docs/ANDROID.md)
- [Backend Go](https://github.com/waste-not-app/waste-not-backend) dan [worker AI](https://github.com/waste-not-app/waste-not-ai)
- [Design system dan spesifikasi](<docs/WasteNot-Product-Design-Kit update/WasteNot/README.md>)
- [Roadmap Android](https://github.com/waste-not-app/waste-not-android/issues/2)
- [Kanban](https://github.com/orgs/waste-not-app/projects/1/views/1)

## Toolchain

Kotlin 2.3.20, AGP 9.0.1, Gradle 9.1.0, JDK 17, Jetpack Compose, Material 3, Navigation 3 dan CameraX. Minimum Android 11/API30; compile/target SDK36. Versi dependency tercatat di [version catalog](gradle/libs.versions.toml). Pengujian utama menggunakan Android terbaru.

## Build dan pemeriksaan

Siapkan JDK17, Android SDK Platform36, serta `ANDROID_HOME` atau `sdk.dir` dalam `local.properties` lokal.

```sh
./gradlew lintDebug lintStaging lintRelease testDebugUnitTest
./gradlew assembleDebug assembleStaging assembleRelease assembleDebugAndroidTest -Pwastenot.hotReload=false
python3 scripts/verify_build_environments.py
python3 scripts/verify_release_workflow.py
python3 scripts/verify_live_data.py app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/staging/app-staging.apk app/build/outputs/apk/release/app-release-unsigned.apk
```

Alamat bawaan ketiga varian: `https://waste-not-api.bccdev.id/v1/`. Override lingkungan memakai `wastenot.debugApiBaseUrl`, `wastenot.stagingApiBaseUrl`, atau `wastenot.releaseApiBaseUrl`; nilai kosong ditolak. HTTP hanya diizinkan pada debug dengan override localhost/emulator yang eksplisit. Staging memakai package `.staging` dan HTTPS. Release memerlukan signing sebelum distribusi; pipeline publikasi tetap mewajibkan variable `WASTENOT_RELEASE_API_BASE_URL`.

Data terkontrol dan adapter lokal untuk pengujian berada di `app/src/testFixtures` dan `app/src/androidTest`, tidak masuk APK debug/staging/release. Pengujian perjalanan UI mengaktifkan sumber tersebut melalui runner pengujian saja; pengujian backend nyata tetap opt-in dan memakai adapter remote. Pemeriksaan `verify_live_data.py` menolak adapter lokal dalam source aplikasi maupun DEX APK.

[Setup lingkungan](docs/ANDROID_SETUP.md), [CI/CD](docs/CICD_SETUP.md), dan [bukti QA](docs/qa/iteration-2/) memuat rincian serta batas pemeriksaan. Catatan checkpoint lama tetap menjadi bukti pada commit masing-masing.
