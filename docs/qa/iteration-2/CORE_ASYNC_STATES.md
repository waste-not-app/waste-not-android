# State loading, kosong dan gagal pada layar inti

8 November 2026. Enam layar inti memakai pola C29 yang sama dengan kit. `AsyncStatePanel` memberi simbol fungsional, judul, penjelasan sesuai konteks, serta progress atau satu aksi pemulihan. Susunan mengikuti DESIGN §6 dan `.state-box`: bidang simbol 64 dp/radius 20 dp, ikon 30 dp, heading 19/24 sp, padding 32/12 dp, gap 16 dp dan penjelasan maksimum 270 dp. Warna berasal dari tema; panel tidak menambah kartu/shadow baru.

## Perubahan

| Layar | Keadaan yang diselaraskan | Perilaku yang dipertahankan |
|---|---|---|
| S04 Beranda | Loading awal dan gagal tanpa snapshot | Snapshot lama, angka server dan banner gagal refresh tetap tersedia. Hari kosong dengan data sukses tidak disamakan dengan hasil yang belum diketahui. |
| S07 Riwayat | Loading awal, gagal tanpa data, kosong pertama dan kosong karena filter | Angka nol hanya ditampilkan setelah hasil sukses; retry, input manual dan reset filter tetap punya tujuan. Error dengan catatan lama mempertahankan daftar/banner ringkas. |
| S08 Detail | Loading, gagal awal dan record tidak tersedia | Retry/Kembali ke riwayat tetap terarah. Record yang tidak ada tidak menawarkan edit/hapus. |
| S11 Panduan | Loading awal, katalog kosong, hasil pencarian kosong dan error | Katalog tanpa kriteria memakai “Muat ulang panduan”; hasil dengan kriteria memakai “Hapus pencarian”. Artikel lama dan pesan refresh/page-error tetap mengikuti pola ringkas. |
| S12 Detail panduan | Loading, tidak tersedia dan error | Kembali ke daftar atau retry; artikel tidak tersedia tidak dapat membuat prefill editor. |
| S13 Statistik | Loading/gagal awal melalui SummaryStatus, serta periode kosong | Selector 7/30 tetap dapat dipakai; tanggal snapshot lama dan banner refresh tidak diganti dengan data periode lain. |

Fallback awal persiapan editor memakai panel bersama yang sama. Jalur simpan, UUID, provenance AI, perhitungan XP, autentikasi dan endpoint tidak berubah. Judul state mempunyai heading/live-region; ini dukungan pengumuman status, bukan bukti spoken TalkBack.

Perbaikan perilaku konkret: sebelumnya Riwayat mengumumkan `0 aktivitas tercatat.` saat hasil masih loading, dan Panduan tanpa query/kategori menyediakan tombol clear yang tidak mengubah hasil. Keduanya kini membedakan keadaan yang diketahui dari yang belum diketahui serta menawarkan aksi yang sesuai.

## Pemeriksaan

`CoreAsyncStateDesignTest` merender state terkontrol pada dua tema, memeriksa heading/simbol 64 dp, callback pemulihan, ketiadaan angka nol pada hasil yang belum diketahui, serta pembatasan record/artikel yang tidak tersedia. Frame memuat komponen layar sebenarnya, bukan respons backend/AI baru. Pengaturan system bar fixture mengikuti tema; fixture tetap tidak membuktikan inset/navigasi MainActivity seluruh layar.

Baseline aplikasi source 9c97812, APK `eb3688bb344d5a88134b10340ddcd6eef67c1e0256e8bf62b86ff31f77d355e9`, mereproduksi **tiga kegagalan yang dituju dalam 55,912 detik** dengan 36 PNG. Sesudah perubahan, **tiga metode native lulus dalam 58,420 detik** dengan 36 PNG. Kedua eksekusi memakai APK penguji identik `0141163cc724c51b2088af5568977e82ac8aa2ce263aea2042cf75e5551eb852`. APK aplikasi sesudah perubahan `55448ce0bcbd0e2aad44a811c5961f55e4e33510cff7f081641b7954686328f9`; checksum kedua APK yang benar-benar terpasang diperiksa.

Hasil akhir memakai pemeriksaan query field dan draw window sebelum capture. Frame percobaan awal yang belum menampilkan query dengan stabil disimpan terpisah, tidak masuk galeri ini. Judul error ringkasan dipendekkan agar tidak menyisakan satu kata pada baris kedua. Galeri final memperlihatkan query `karton` pada hasil kosong dan pesan error yang telah dirapikan.

- [72 PNG asli dan galeri 18 state](core-async-review/index.md), [manifest](core-async-review/capture-manifest.json).
- [Hasil baseline](core-async-review/baseline/result.json), [log baseline](core-async-review/baseline/tests.log), [hasil sesudah](core-async-review/after/result.json), [log sesudah](core-async-review/after/tests.log).
- [APK/source baseline](core-async-review/baseline-build.json) dan [APK/source sesudah](core-async-review/after-build.json). Kesembilan file perubahan cocok dengan commit produk `649827e1b74aa8fe795de514926de9edc5b936c7`. APK native dibekukan sebelum hook mengaktifkan kembali konfigurasi debug default.
- [Empat perbandingan area isi](core-async-review/cached-pixel-comparison.json) Beranda/Riwayat saat refresh gagal identik piksel sebelum/sesudah. Crop `[0,90,1170,2470]` mengeluarkan status bar/gesture; ini bukan klaim seluruh PNG atau semua data cache identik.
- Callback yang diperiksa: empat retry dan dua input manual Riwayat; empat reload/retry serta dua kembali ke daftar Panduan, ditambah clear query/category; empat retry, dua Catat aktivitas dan dua Lihat riwayat pada kelompok Ringkasan/Detail. Record/artikel yang tidak tersedia tidak menawarkan edit, hapus atau prefill.
- [Lint dan 239 unit tests](core-async-review/local-check-result.json) lulus, nol gagal/error/skip, melalui hook commit default termasuk HotSwan. Review source Standards: 0 temuan; Spec: 0 temuan terhadap dasar 323e589, dikonfirmasi lagi pada delta terakhir. Review source tersebut tidak menggantikan hasil render/native.

## APK checkpoint

`WasteNot-preview-backend-async.apk`, **34.988.887 byte** (sekitar 35 MB), SHA `d25a6e874f1076d22a0d93549687e80a5f8abbaebd616878080e3a63364c5aab`. Package `com.example.wastenot.staging`, versi 1.0.0-staging, minimum Android11/API30 dan target36. [Konfigurasi](core-async-review/staging/StagingBuildConfig.java) serta pemeriksaan DEX memastikan adapter remote/origin Go publik, dummy/hot reload mati dan URL emulator tidak menjadi konfigurasi build. [Policy paket](core-async-review/staging/staging-network-policy.log) melarang cleartext.

[Verifier SDK/font](core-async-review/staging/staging-minimum-sdk.log) dan [signature](core-async-review/staging/apksigner.log) lulus: adjustResize, kelima TTF Jakarta/OFL dan signature v2. [Upgrade/cold startup API37](core-async-review/staging/staging-launch.log) sampai S02 lulus, dengan [capture](core-async-review/staging/staging-login.png), [hierarchy](core-async-review/staging/staging-login.xml) dan [identitas paket terpasang](core-async-review/staging/staging-checkpoint.json). Fase pemasangan staging mempertahankan checksum paket normal yang dipakai tes native. Pengaturan display/font dipulihkan dan emulator QA dihentikan.

Smoke staging mencakup layar masuk kosong. Tidak ada akun baru, submit login, upload atau generasi AI dalam checkpoint ini. Alur backend/AI nyata sebelumnya tetap terikat pada [bukti source-nya](AI_LIVE_UI.md); hasil itu tidak diklaim dijalankan ulang pada APK staging ini.

Remote backend kini `dc127354b7652c5e12bc2543838daa74d0fce443` dan AI tetap `baadd773aac01f7ef48c8beb15f62875c56f6b3f`. Refaktor Fiber backend tidak mengubah ANDROID.md, kontrak core, bundle AI maupun pin terhadap c1e81c2. Ini pemeriksaan kompatibilitas sumber, bukan penerimaan ulang runtime backend baru.

Gate checkpoint: **Hard PASS** untuk heading, simbol, scroll/kontrol yang diamati dan recovery callback; **Purpose PASS** pola C29/token dari kit serta pemisahan unknown/zero; **Liveliness PASS** ENERGY2/RHYTHM2/MOTION1 tetap; **Craft PASS** pada dua tema, source/APK yang dipin dan perbandingan empat area isi. Font/input lain, spoken TalkBack dan state di luar matriks tersebut belum diterima secara menyeluruh. CI/publikasi tetap dilaporkan terpisah pada PR.

Ini checkpoint enam layar/state yang disebutkan. Pagination/filter riwayat melalui API, migrasi preferensi ke DataStore, state kamera aktif, seluruh input/font/TalkBack dan penerimaan akhir rilis tetap mengikuti backlog serta [urutan UI](../../UI_FIRST_PLAN.md). Lulusnya contoh state tidak menutup seluruh goal.
