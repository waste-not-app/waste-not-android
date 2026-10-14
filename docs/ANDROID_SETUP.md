# Setup dan fondasi Android WasteNot

Pekerjaan awal untuk [A01 / issue #3](https://github.com/waste-not-app/waste-not-android/issues/3). Acuan produk yang digunakan adalah [kit terbaru](<WasteNot-Product-Design-Kit update/WasteNot/README.md>), bukan salinan `docs/design-kit/` yang lama.

## Kondisi starter

Bagian ini mencatat baseline audit pada `e923f3c`. Perkembangan implementasi setelah audit dicatat per issue di [docs/tasks](tasks/README.md).

Satu module `:app`, Kotlin 2.3.20, AGP 9.0.1, Gradle 9.1.0, JDK 17, Compose BOM 2026.03.01, Navigation 3, min SDK 24, compile/target SDK 36. Versi dipertahankan dari starter dan dicatat di version catalog serta Gradle wrapper.

`HomeScreen` masih memakai data contoh dan callback pencatatan belum terhubung. Auth, repository HTTP, CRUD, statistik dan pipeline AI belum diimplementasikan. Pemeriksaan starter tidak membuktikan acceptance fitur tersebut.

Package dan application ID dasar tetap `com.example.wastenot` agar identitas build awal tidak berubah. Tim perlu menetapkan identitas publik sebelum distribusi final. Staging memakai suffix `.staging` sehingga data instalasinya terpisah.

Staging tidak debuggable dan memakai debug certificate untuk instalasi uji. HotSwan 2.0.1 pada starter tidak menyediakan task rewrite suspend-lambda staging, sehingga hot reload tetap terbatas pada debug; staging/release memakai jalur build biasa.

## Build dari checkout baru

Pasang JDK 17 dan Android SDK Platform 36. Arahkan `ANDROID_HOME` ke SDK, atau isi `sdk.dir` dalam `local.properties` yang diabaikan Git.

```sh
./gradlew lintDebug lintStaging lintRelease testDebugUnitTest
./gradlew assembleDebug assembleStaging assembleRelease
python3 scripts/verify_build_environments.py
```

Starter menyediakan task unit test debug (termasuk renderer Navigation Graph); task lint tersedia untuk debug/staging/release. Pemeriksaan APK menguji pemisahan konfigurasi ketiga varian. Build pertama membutuhkan koneksi untuk dependency. `assembleRelease` tanpa konfigurasi signing menghasilkan APK unsigned untuk pemeriksaan build, bukan artifact siap distribusi. Signing hanya membaca empat environment variable dari penyimpanan rahasia: `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Seluruh nilai harus tersedia bersama; konfigurasi parsial ditolak.

## Konfigurasi lingkungan

| Build type | Property Gradle | Default | Kebijakan |
|---|---|---|---|
| debug | `wastenot.debugApiBaseUrl` | `http://10.0.2.2:8080/v1/` | HTTP hanya untuk emulator/localhost |
| staging | `wastenot.stagingApiBaseUrl` | kosong | HTTPS; application ID `.staging` |
| release | `wastenot.releaseApiBaseUrl` | kosong | HTTPS; tidak membawa URL debug |

Nilai kosong berarti backend belum dikonfigurasi; bukan alamat layanan produksi. Repository HTTP pada A03 harus menampilkan kegagalan konfigurasi secara terarah sebelum mengirim request jika nilainya kosong. Base URL adalah konfigurasi publik dalam `BuildConfig.API_BASE_URL`, bukan tempat menyimpan credential atau API key.

```sh
# Backend lokal di host emulator
./gradlew assembleDebug

# HTTPS staging: isi URL nyata dari tim melalui environment lokal
./gradlew assembleStaging "-Pwastenot.stagingApiBaseUrl=$WASTENOT_STAGING_API_BASE_URL"

# HTTPS release: isi URL nyata dari tim melalui environment lokal
./gradlew assembleRelease "-Pwastenot.releaseApiBaseUrl=$WASTENOT_RELEASE_API_BASE_URL"
```

Base URL harus absolute, memiliki host/port yang valid, serta tidak memuat user info, query atau fragment. Trailing slash dinormalisasi. Cleartext debug dibatasi ke `10.0.2.2`, `localhost` dan `127.0.0.1`; staging/release menggunakan kebijakan jaringan HTTPS. Implementasi mengikuti [Network Security Configuration Android](https://developer.android.com/privacy-and-security/security-config) dan [build variants](https://developer.android.com/build/build-variants).

Backup cloud/device-transfer data internal dinonaktifkan pada baseline. Aturan yang lebih selektif untuk preferences harus direview bersama penyimpanan token/draf A04–A05; password dan refresh token tidak boleh masuk backup.

## Branch dan CI/CD

Repo yang diaudit baru memiliki branch remote `main`. Gunakan branch tugas dari commit dasar yang sudah diverifikasi; `develop` dapat menjadi keputusan workflow tim berikutnya.

```sh
git switch -c codex/A01-nama-perubahan
./gradlew lintDebug lintStaging lintRelease testDebugUnitTest assembleDebug assembleStaging assembleRelease
git add <file-yang-terkait-tugas>
git commit -m "chore(android): describe the completed change (#3)"
git push -u origin HEAD
```

`Android CI` mencakup `codex/**`, lint tiga build type serta unit test debug, pengujian konfigurasi URL, APK debug/staging/release, serta artifact laporan. CI tidak memakai credential signing atau deployment. `Android Release & Deploy` tetap mengikuti trigger main/tag/manual yang sudah ada, memeriksa lint release dan unit test debug dari source bersama, dan menolak deployment jika APK tidak ditandatangani. Push branch tugas tidak menjalankan workflow deploy.

Input versi manual diperlakukan sebagai environment value lalu diperiksa agar aman sebagai nama file. URL API release dapat disediakan lewat GitHub repository variable `WASTENOT_RELEASE_API_BASE_URL`. Lihat [panduan CI/CD](CICD_SETUP.md) untuk nama secrets dan operasi rilis.

## Bukti dan keputusan yang masih perlu

- Catat hasil aktual lint, unit test dan build pada PR/issue terkait; hasil web mockup bukan hasil Android.
- Install/launch dan pengujian UI memerlukan emulator atau perangkat Android. Tandai belum diuji bila tidak ada perangkat yang tersedia.
- Identitas publik, URL backend nyata dan kontrak lintas tim tetap keputusan B01/tim.
- Kredensial signing yang pernah dicatat di dokumentasi perlu dirotasi oleh pengelola. Menghapus nilainya dari file terbaru tidak menghapus riwayat Git; jangan mengganti certificate signing tanpa mempertimbangkan kompatibilitas update APK.
