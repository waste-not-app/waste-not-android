# A01 — Fondasi dan konfigurasi environment

Issue [#3](https://github.com/waste-not-app/waste-not-android/issues/3) · PR [#88](https://github.com/waste-not-app/waste-not-android/pull/88) · status In review.

## Yang dikerjakan

- Mempertahankan satu module, Navigation 3, minSdk 24, target SDK 36, dan toolchain starter; audit dicatat di [ANDROID_SETUP.md](../../ANDROID_SETUP.md).
- Menambahkan base URL debug/staging/release, validasi URL/signing, kebijakan jaringan lokal debug terbatas, serta pengecualian backup/transfer data internal.
- Memperluas GitHub Actions untuk branch tugas, lint tiga varian, unit test, tiga APK, pengujian konfigurasi/paket, dan artifact laporan. Input versi/deployment diperiksa; workflow memakai runner/Actions yang didukung.
- Memperbarui panduan arsitektur/CI sesuai repo aktual. HomeScreen masih contoh dan callback pencatatan masih TODO. Identitas publik dan endpoint bersama belum ditetapkan.

Commit: `2a417d9`, `7ef51ab`, `36eac6a`.

## Bukti

- Lint debug/staging/release, unit test starter, dan tiga build APK lulus pada fondasi. CI head `36eac6a`: [push](https://github.com/waste-not-app/waste-not-android/actions/runs/38454237026), [PR](https://github.com/waste-not-app/waste-not-android/actions/runs/38454245312).
- 13 pemeriksaan URL/signing/policy APK dan lima fixture pengemasan release lulus.
- Kode produksi lanjutan `2dd1adb` berhasil dipasang/dibuka untuk debug, staging, dan salinan release pada emulator API 36 x86_64. Status peluncuran `ok`, proses aplikasi terdeteksi. Salinan release untuk uji ditandatangani debug key lokal; artifact release asli tetap unsigned.
- Crash buffer emulator mencatat servis radio/audio bawaan saat boot, tanpa entri package WasteNot. Ini bukti startup terbatas.

## Checklist issue

Audit starter, konfigurasi environment, dokumentasi, pemeriksaan release, dan install/startup telah memiliki bukti. Issue tetap terbuka untuk review; PR belum di-merge atau di-deploy.

## Batas dan tindak lanjut

Fondasi dilanjutkan oleh root/auth/editor pada `codex/android-auth-navigation`; starter HomeScreen dan callback TODO sudah diganti. APK native minimum API 24 kini diperiksa oleh gate paket dan uji perangkat di [laporan Iteration 1](ITERATION_1_QA.md). Gate juga memastikan adjustResize berada pada MainActivity, bukan application. URL staging/release dan identitas publik masih memerlukan keputusan tim. Credential signing yang pernah berada di riwayat perlu ditinjau pengelola tanpa mengganti certificate secara sembarang. Rincian operasi ada di [CICD_SETUP.md](../../CICD_SETUP.md).
