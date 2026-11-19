# Semua varian memakai akun dan data backend

8 November 2026. Source produk `e499511658ca252ab8698ca237c73b7ca27dd0fe` menghapus pemilihan adapter lokal dari aplikasi. Instalasi baru membuka Masuk/Daftar. Identitas akun, catatan, ringkasan, panduan dan scan menggunakan layanan Go HTTPS; kegagalan jaringan tetap menjadi kegagalan yang dapat dipulihkan, tanpa data sukses buatan.

## Perubahan aplikasi

- `AppContainer` selalu menyediakan `RemoteAccountSession`, `RemoteGuideStore`, `RemoteActivityStore` dan `RemoteScanRepository`. Boolean pemilih sumber dan jalur UI terkait dihapus, termasuk pengecualian provenance pada scan.
- Setiap metode AI yang diteruskan dari rekomendasi harus memenuhi READY/HANDLING, konfirmasi, kategori dan expiry. Respons `prototype_only` tidak dapat mengisi metode maupun provenance. Unit test mengunci pembatasan ini.
- Semua adapter lokal dipindahkan ke `src/testFixtures`. Preview komponen berada di `src/androidTest`. Runner pengujian mengganti application hanya saat instrumentation; aturan fixture memasang dan melepas sumber terkontrol selama tes navigasi. APK aplikasi tidak membawa kelas-kelas tersebut.
- Debug, staging dan release memakai default `https://pred-autoscaler.bccdev.id/wastenot/api/v1/`. URL kosong ditolak; override HTTP debug dibatasi localhost/emulator. Staging/release tetap HTTPS.
- Copy mode lama dan pesan editor yang masih mengatakan penyimpanan belum tersedia dihapus. Tampilan gagal simpan yang sebenarnya tetap ada.
- CI mencakup branch tugas standar dan memeriksa source, DEX serta assets agar adapter lokal tidak terpaket. Workflow publikasi juga menjalankan pemeriksaan ini sebelum menyiapkan APK.

Fixture pengujian bukan sumber data produk. Pemisahan application pada instrumentation mengikuti [panduan Android](https://developer.android.com/training/dependency-injection/hilt-testing#custom-test-runner), tanpa menambah dependency Hilt. Direktori Kotlin bersama didaftarkan hanya pada source set test melalui [AndroidSourceSet.kotlin pada AGP9](https://developer.android.com/build/migrate-to-built-in-kotlin#add-kotlin-source-directories).

## Pemeriksaan dan alur nyata

[Build manifest](live-backend-only/build-manifest.json) mengikat file sumber dan APK yang dibekukan sebelum hook commit mengubah output pengembangan. File produk cocok dengan commit `e499511`. [Hook lint dan unit](live-backend-only/hook-checks.json): lint debug nol error; **239 unit test** lulus, nol gagal/error/skip. Tidak ada hasil unit staging/release tersendiri yang diklaim.

[Build serta gate paket](live-backend-only/package-gates.log) lulus untuk ketiga varian: URL valid/berbahaya, URL kosong, signing parsial, kebijakan jaringan/backup dan isolasi konfigurasi. `verify_live_data.py` memeriksa tidak adanya mode dan copy lama di source, adapter lokal dalam DEX/assets, serta keberadaan adapter remote. [Paket checkpoint sebelumnya](live-backend-only/before-package.json) masih membawa dua namespace adapter lokal walaupun dipilih remote; paket baru tidak membawanya.

Empat metode native lulus pada API37:

| Pemeriksaan | Bukti dan batas |
|---|---|
| Default dependency aplikasi | Konstruktor `AppContainer` menyediakan sumber remote dan tidak membuat akun otomatis. |
| HTTPS tanpa autentikasi | Metadata layanan nyata menolak request tanpa bearer dengan 401 dan request ID. |
| Isolasi fixture navigasi | Tes terkontrol membuka lima tab dan Back keluar dari Beranda; sumber ini dipasang hanya melalui runner test. |
| Akun dan catatan nyata | Daftar melalui UI, create 250 g, ringkasan/statistik/profil, edit menjadi tiga benda, delete, dan logout memakai backend publik. |

[Hasil yang direkonsiliasi](live-backend-only/live-account-result-reconciled.json), [log konfigurasi/HTTPS](live-backend-only/configuration-and-https.log), [log fixture terisolasi](live-backend-only/isolated-navigation-fixture.log), [log alur nyata](live-backend-only/live-account-ui.log), dan [enam tahap server/UI](live-backend-only/live-account-evidence.json). Data input uji menggunakan akun sementara yang benar-benar terdaftar di backend; tidak memakai akun pribadi pengguna.

Setelah create, server dan UI menunjukkan 250 g serta 10 XP. Edit menjadi tiga benda tidak menambah XP. Setelah delete, ringkasan dan progres kembali nol. Pemeriksaan server menemukan tepat satu user, satu session dan satu baris catatan milik run tersebut sebelum cleanup; setelah menghapus identitas uji yang dibatasi email/nama/UUID, ketiganya nol. Pengaturan display/font/tema dipulihkan. Tidak ada seed permanen atau deployment backend pada checkpoint ini.

Penguji aplikasi telah lulus sebelum driver host berhenti karena menemukan sembilan PNG: satu merupakan frame panduan dari percobaan lama. Delapan nama frame yang benar dipilih dan diverifikasi tanpa mengulang tes aplikasi. [Hasil awal](live-backend-only/live-account-result.json) dipertahankan; galeri hanya memuat delapan frame run yang sudah lulus.

[Galeri delapan frame](live-backend-only/index.md) memakai tema terang, 390×844dp, font100%. Angka yang terlihat berasal dari operasi akun backend tersebut. Galeri ini tidak menerima ulang seluruh 20 screen, tema gelap atau seluruh konfigurasi input/font.

## APK yang bisa dicoba

`WasteNot-live-staging.apk`, **34.922.163 byte**, SHA256:

```text
73031bb2905063f10c23220faba71c89d6f6a7a9ac264143a82b18caec9f422c
```

Package `com.example.wastenot.staging`, versi `1.0.0-staging`, minimum Android11/API30, target36, tidak debuggable. Pemeriksa SDK/font memastikan adjustResize, kelima font Jakarta dan lisensi OFL. Signature v2 lulus.

[Startup staging di luar instrumentation](live-backend-only/staging-smoke-reconciled.json) sampai formulir Masuk lulus pada API37, 360×640dp. [Frame awal](live-backend-only/staging-first-observed.png) menunjukkan Email/Kata sandi kosong; [footer](live-backend-only/staging-login-footer.png) dapat dicapai dengan scroll. Driver awal menunggu footer yang masih di luar viewport; formulir dan footer diperiksa tanpa mengulangi instalasi/cold startup. Checksum package debug tetap cocok dengan manifest checkpoint. APK terbaru lokal diperbarui hanya setelah pemeriksaan ini.

Salinan lokal yang bisa dipasang berada di `Documents/Codex/2026-11-08/wastenot-ui/WasteNot-preview-backend-latest.apk`. APK sebelumnya tetap diarsipkan. Build release pada pemeriksaan lokal unsigned; tidak diklaim sebagai rilis terpublikasi.

## Batas checkpoint dan pekerjaan berikutnya

Dokumen [ANDROID.md backend](https://github.com/waste-not-app/waste-not-backend/blob/main/docs/ANDROID.md) dibaca kembali; Android tetap hanya memakai gateway Go, bukan port worker atau key provider. [Respons metadata publik](live-backend-only/public-release.json) tidak memberi release SHA, sehingga checkpoint ini tidak mengklaim identitas commit deployment server dari respons tersebut.

Pengujian provider AI nyata terdahulu tetap terikat pada [APK dan source-nya](AI_LIVE_UI.md). Batch ini memverifikasi pemilihan `RemoteScanRepository` dan pembatasan hasil prototipe, tanpa membuat generasi Gemini baru. [Checkpoint kamera](CAMERA_LIFECYCLE.md), pagination/filter API A18, preferensi DataStore A30, regresi akhir dan status CI/merge tetap dicatat terpisah pada [prioritas UI](../../UI_FIRST_PLAN.md).
