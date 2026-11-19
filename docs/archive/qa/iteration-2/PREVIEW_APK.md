# APK preview dengan backend

7 November 2026. APK memakai source aplikasi `c805775`, termasuk [fokus tombol/tab](KEYBOARD_FOCUS.md), pada build type staging yang sudah tersedia. Package `com.example.wastenot.staging` berbeda dari instalasi normal, sehingga dapat dipasang berdampingan. Versinya `1.0.0-staging`, minimum Android11/API30 dan target36.

Build memakai `wastenot.hotReload=false`, `wastenot.useDummyData=false` dan `wastenot.stagingApiBaseUrl=https://pred-autoscaler.bccdev.id/wastenot/api/v1/`. APK merupakan preview lokal, bukan publikasi rilis main. [Manifest/hash APK](staging-preview/staging-checkpoint.json): 34.968.211byte, SHA256 `bcf134c51fb698404d8f59bb639e408af3429e4482afb2b660b31387240b9f19`.

## Pemeriksaan paket dan runtime

- Signature v2 valid, debuggable false, origin Go publik ditemukan di DEX, cleartext ditolak, URL emulator debug tidak masuk DEX.
- Minimum SDK30, MainActivity adjustResize, kelima TTF Jakarta serta OFL terkemas. Pemeriksa yang sama lulus pada [native debug](staging-preview/debug-sdk-after.log) dan [staging teroptimasi](staging-preview/staging-sdk-after.log).
- Instalasi staging sebelumnya tidak ada pada emulator milik pengujian. APK ini dipasang dan MainActivity dijalankan pada API37; [launch log](staging-preview/staging-launch.log) mencatat COLD/Status ok.
- [Screenshot S02](staging-preview/staging-login.png) dan [hierarchy](staging-preview/staging-login.xml) menunjukkan layar masuk, dua field editable dan tidak ada mode demo atau tab akun. Paket aplikasi aktif dan prosesnya diperiksa. Ini startup signed-out, tanpa membuat akun/backend fixture.
- Metadata versi/update instalasi normal sama sebelum/sesudah. Display dipulihkan setelah uji; aplikasi preview dihentikan. Emulator ditutup setelah bukti disimpan.

Beberapa percobaan persiapan emulator tidak diterima karena dialog System UI/launcher atau proses emulator berhenti sebelum aplikasi dijalankan. Percobaan yang diterima memakai sesi emulator yang dipantau langsung; `system_error_present_before_install=false`, dan tidak ada dialog error sistem pada frame aplikasi. Diagnostik percobaan sebelumnya tetap di luar repo.

## Koreksi pemeriksa font

Build staging mengoptimalkan path font, misalnya `font/plus_jakarta_sans_regular` menunjuk `res/L0.ttf`. Nama resource tetap stabil. Pemeriksa sebelumnya mengasumsikan path ZIP `res/font/plus_jakarta_sans_regular.ttf` dan menghasilkan [KeyError](staging-preview/staging-sdk-before.log) meski font tersedia.

`verify_minimum_sdk.py` kini membaca tabel resource AAPT untuk masing-masing lima weight, lalu tetap mewajibkan entry TTF serta byte yang tidak kosong. Pemeriksaan minimum SDK, activity, larangan aset hot reload dan OFL tetap berjalan. Ini memperbaiki false failure pada nama file teroptimasi, tanpa menurunkan gate. Dua reviewer tidak menemukan masalah pada perubahan tersebut.

## Batas bukti

Empat tes interaksi fokus dan perjalanan Go/YOLO/Gemini memiliki APK debug serta binding source sendiri di [QA fokus](KEYBOARD_FOCUS.md) dan [QA AI nyata](AI_LIVE_UI.md). Startup staging ini tidak dianggap pengulangan seluruh backend/AI pada package baru, seluruh navigasi/state/font, TalkBack atau perangkat fisik. Pemeriksaan CI/publikasi tetap terpisah; keberhasilan APK lokal tidak mengubah run gagal upload karena kuota menjadi hijau.
