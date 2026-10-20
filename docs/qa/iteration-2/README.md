# Checkpoint dock, Beranda dan dummy

Kelanjutan S07/S10 tersedia pada [bukti Riwayat dan filter](HISTORY.md), S08/D01 pada [bukti Detail dan hapus](DETAIL.md), S06 pada [bukti feedback simpan](SAVE.md), S05/S09 pada [bukti editor Catat/Ubah](EDITOR.md), serta retry/hasil simpan belum pasti pada [bukti submission](SUBMISSION.md), serta transisi editor dan gestur keyboard pada [bukti navigasi](NAVIGATION_MOTION.md), serta gradasi/label tombol utama pada [bukti C03](ACTIONS.md). Angka tes pada halaman ini merujuk checkpoint awal `1693e7c`, bukan total source terbaru.

19 Oktober 2026. Branch `codex/android-iteration-2-design`, dasar `ceb8707`. Ini bukti untuk perubahan checkpoint, **bukan penerimaan seluruh layar WasteNot**. Matriks pekerjaan lengkap ada di [Iteration 2](../../tasks/ITERATION_2_DESIGN.md).

## Perbandingan visual langsung

Referensi dirender dari `design-system.html` v1.3 dengan Chromium dan font lokal. Hasil Android berasal dari APK native pada emulator API 24. Data awal mengikuti kuantitas kit; nama dan tanggal memakai sumber dummy baru. Reference memiliki frame/status bar dokumentasi, sedangkan Android memiliki system bars asli. Lebar Android ≈390 dp diperoleh dari densitas 443 pada layar fisik 1080×1920; ukuran gambar bukan satuan dp.

| Tema | Render kit v1.3 | Android ≈390 dp |
|---|---|---|
| Terang | ![Referensi terang](reference-home-light.png) | ![Android terang](home-light-390.png) |
| Gelap | ![Referensi gelap](reference-home-dark.png) | ![Android gelap](home-dark-390.png) |

Komposisi sapaan, ringkasan pinus, input manual sekunder, Reduce lime dan dock kini mengikuti arah kit. Dock memiliki radius 26 dp, lima tujuan, bidang ikon aktif lime dan tombol kamera di tengah atas. Baseline opak memang diizinkan DESIGN §2; tidak ada klaim backdrop blur. Penyempurnaan tipografi/detail material dan perbandingan layar lain masih berlanjut. Belum ada klaim semua screen sudah sama dengan mockup.

## Perangkat dan regresi

APK native memakai `-Pwastenot.hotReload=false`, sehingga mendukung API 24 tanpa interpreter HotSwan.

- **101 tes debug unit, 0 gagal/error/skip**. Build debug/aplikasi instrumentasi dan lint debug lulus pada source terakhir checkpoint.
- **API 24, 360 dp, font normal: 11 kasus runner, 10 dijalankan lulus; satu kasus gestur khusus API 36 tidak dijalankan. 66,461 detik.** Mencakup tiga kasus adapter dummy, perbandingan presisi statistik, journey baru, serta regresi tab/Back/filter/range/rotasi/dirty/create/edit/logout/keyboard.
- **API 24, 360 dp, font 200%: journey desain lulus, 19,098 detik.** Kelima tab, target sentuh ≥48 dp, sheet, manual, tema dan recreate diperiksa.
- **API 24, 600 dp, font 200%: journey yang sama lulus, 18,450 detik.** Aksi rail ditata vertikal agar kata Catat/Scan tidak pecah. Tes memeriksa batas baris dari `TextLayoutResult`, sehingga bukan sekadar menemukan string di semantics.
- Pemeriksaan window nyata setelah cold start memastikan Beranda tersedia, tombol Tutup mengembalikan dock, dan Back sistem saat sheet terbuka menutup modal tanpa keluar dari aplikasi. Driver membaca hierarchy window setiap langkah; pemeriksaan ini terpisah dari dispatcher Back editor pada suite Compose.
- Baseline ≈390 dp juga menjalankan suite 11 kasus runner dengan 10 kasus lulus dan satu kasus API 36 tidak dijalankan, 63,662 detik. Setelah itu susunan rail disempurnakan; tiga konfigurasi di atas dijalankan kembali.
- Gate native lulus untuk API 24, `MainActivity.adjustResize`, lima font dan lisensi OFL. Verifier environment/isolasi APK serta lima skenario packaging release lulus. Pemeriksaan status Actions mengikuti SHA pada PR, bukan dianggap selalu lulus dari dokumen ini.

Tes dummy memeriksa rekonstruksi store/sesi, idempotency key yang tetap berlaku, konflik version, jumlah record tidak berganda, isolasi namespace akun, data rusak tidak diganti seed sukses, login ulang akun fixture, logout, serta password tidak masuk preferences. Ini bukan tes autentikasi atau database server. Pergantian proses sistem untuk draf dummy baru belum diaudit ulang.

| Konfigurasi | Terang | Gelap | Sheet |
|---|---|---|---|
| 360 dp, normal | [Beranda](home-light-360.png) | [Beranda](home-dark-360.png) | [Sheet](capture-sheet-360.png) |
| 360 dp, 200% | [Beranda](home-light-360-font200.png) | [Beranda](home-dark-360-font200.png) | [Sheet](capture-sheet-360-font200.png) |
| 600 dp, 200% | [Rail](home-light-600-font200.png) | [Rail](home-dark-600-font200.png) | [Sheet](capture-sheet-600-font200.png) |

Pada teks 200%, konten Beranda dapat digulir dan label tab membungkus. Screenshot Beranda besar diambil setelah menggulir ke angka utama; bukan bukti header di posisi paling atas. Screenshot API 24 juga memuat UI sistem lama. Sheet memakai navigation bar pinus pada API 24–25 agar ikon sistem putih tetap terbaca.

## Gate perubahan checkpoint

- **Hard PASS untuk kontrol yang diubah:** perjalanan perangkat membuka lima tab, profil dari header, input manual Beranda/sheet, riwayat, detail, tema, lalu memulihkan state setelah recreate. Tidak ada tombol masuk demo. Kamera masih memiliki keterangan belum tersedia; alur foto/AI belum diterima.
- **Purpose PASS:** alasan warna, font, ikon, komposisi, radius, gradient dan baseline opak mengikuti DESIGN §1–5/12; dicatat pada dokumen Iteration 2. Sumber angka adalah fixture PRD dan catatan dummy tersimpan.
- **Liveliness PASS untuk checkpoint:** ENERGY 2 / RHYTHM 2 / MOTION 1, satu angka utama, daftar sekunder, aksen lime dan fade 180 ms. Kualitas predictive Back per frame belum dievaluasi ulang.
- **Craft PASS untuk keterbacaan konfigurasi yang diuji:** tema terang/gelap, font normal/200%, dock/rail dan keyboard diperiksa. Kontras aktual dihitung: teks lime ≥9,37:1; teks sekunder canvas terang 5,24:1 dan gelap 6,50:1; teks summary pada highlight paling terang 4,63:1; ikon sistem putih pada pinus 11,80:1. Gate ini tidak mengubah status layar lain menjadi selesai.

## Pekerjaan berikutnya

Lengkapi komposisi/form/detail/feedback, Riwayat dan filter sheet, Panduan/detail, Statistik/grafik, Profil/achievement/pengaturan, lalu alur foto/AI sesuai matriks Iteration 2. Audit state kosong/error dan proses mati setelah masing-masing alur tersedia. Backend HTTP diintegrasikan setelah layanan ada; stub kamera dan placeholder Panduan masih terlihat dan belum siap sebagai hasil akhir.
