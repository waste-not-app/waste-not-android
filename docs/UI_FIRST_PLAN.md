# Prioritas UI WasteNot

Arahan pengguna, 6 November 2026: lengkapi screen yang dibutuhkan, cocokkan dengan design system/mockup dan navigasinya, lalu lakukan pengujian menyeluruh. Integrasi yang sudah tersedia dipertahankan. Build dasar dan pemeriksaan visual selama implementasi membantu memastikan layar dapat dibuka; pengujian backend, lintas perangkat dan regresi mendalam dilakukan setelah rangkaian UI lengkap.

## Cakupan inti dan AI

Acuan: `docs/WasteNot-Product-Design-Kit update/WasteNot/SCREEN_SPEC.md`, PRD dan mockup dalam folder yang sama. Inventaris di bawah membuktikan keberadaan implementasi sumber, bukan penerimaan visual atau fungsi yang telah selesai.

| ID | Layar/alur | Implementasi sumber yang tersedia |
|---|---|---|
| S01 | Pemulihan sesi | StartupScreen, MainNavigation |
| S02–S03 | Masuk dan daftar | AuthScreen, AuthNavigation |
| S04 | Beranda | OverviewScreen |
| S05/S09 | Catat dan ubah aktivitas | LogEditorScreen |
| S06 | Catatan tersimpan | SaveConfirmationScreen melalui detail |
| S07 | Riwayat | HistoryScreen |
| S08 | Detail catatan | LogDetailScreen |
| S10 | Filter riwayat | Sheet filter pada HistoryScreen |
| S11–S12 | Panduan dan detail | GuideListScreen, GuideDetailScreen |
| S13 | Statistik | StatisticsScreen |
| S14 | Profil dan progres | ProfileScreen |
| S16 | Pengaturan | SettingsScreen, ThemePicker |
| S21 | Kamera dan tinjau foto | ScanCamera, ScanSheet PHOTO |
| S22 | Objek dan material | ScanSheet OBJECT, katalog benda |
| S23 | Kondisi benda | ScanSheet CONTEXT |
| S24 | Review saran | ScanSheet RECOMMENDATION |
| S25 | Fallback AI | ScanSheet FAILURE, input manual |
| D01–D04 | Hapus, keluar, buang draf, konflik | DeleteLogDialog, LogoutDialog, DiscardChangesDialog, EditConflictDialog |

S20 adalah dokumentasi state yang diterapkan pada layar terkait, bukan menu produksi. S15 Achievement dan S17 Pengingat berstatus Should; S18 Lampiran foto Stretch; S19 Titik daur ulang Could. Status opsional dan aturan capability dari spesifikasi tetap berlaku; inventaris ini tidak mengubahnya menjadi fitur produksi yang sudah tersedia.

## Urutan kerja

1. Tuntaskan struktur dan isi setiap layar inti/AI serta dialog yang diperlukan; identifikasi placeholder dan aksi tanpa tujuan.
2. Rapikan hierarki, komponen, warna, tipografi, jarak, bottom navigation, header, sheet dan transisi mengikuti kit. Periksa hasil render saat mengimplementasikan, dengan satu alur utuh sebagai unit kemajuan.
3. Pastikan pengguna dapat berpindah antarlayar, kembali, menutup modal dan melanjutkan draf tanpa kehilangan konteks. State loading/kosong/gagal memiliki tampilan dan aksi yang jelas.
4. Setelah rangkaian UI lengkap, lakukan pengujian fungsional/backend/AI, regresi lintas konfigurasi dan aksesibilitas; perbaiki temuan, jalankan CI, lalu siapkan APK checkpoint beserta perbandingan screenshot.

Kemajuan UI dilaporkan sebagai daftar layar yang lengkap dan yang masih perlu diselaraskan. Persentase checklist backlog historis bukan persentase kesiapan visual. Pengujian yang sudah lulus tetap menjadi bukti pada commit masing-masing; pengujian baru tidak diperluas terus sebelum tahap implementasi UI selesai.

Permintaan merge PR yang aman tetap berlaku. Perubahan scan yang sedang dikerjakan berada di branch terpisah; hasil review/CI harus diperiksa sebelum merge, dan status merged tidak menandakan seluruh tampilan telah diterima.

## Checkpoint tampilan awal, 6 November 2026

- Inventaris sumber: **20/20 screen wajib** dan **4/4 dialog** tersedia.
- Cakupan snapshot awal: **20/20 screen wajib** dan **4/4 dialog**, 67 PNG pada satu ponsel API 37, 390×844 dp. Ini 100% cakupan ID tampilan awal, bukan 100% penerimaan visual atau kesiapan rilis.
- Beranda, riwayat, detail, editor, panduan, statistik, profil, pengaturan, auth, fallback dan dialog memakai token/komponen kit. Scan diselaraskan pada header/tahap, konfirmasi material/benda, kondisi bertahap dan kartu saran. Navigasi demo filter → detail → edit → konfirmasi simpan serta dialog telah dibuka melalui aplikasi.
- S01–S03, S25 dan D04 juga dirender melalui fixture komponen untuk memeriksa tampilan tanpa menunggu jaringan. Bukti fixture tidak membuktikan pemulihan sesi atau konflik backend nyata.
- Galeri dan batas bukti: [UI_SCAN_PREVIEW.md](qa/iteration-2/UI_SCAN_PREVIEW.md). S21–S24 baru direkam pada tema terang. Snapshot tidak mencakup seluruh state, semua bagian scroll, ukuran/font lain, TalkBack atau perangkat fisik.

Tahap implementasi struktur dan penyesuaian tampilan awal sudah memiliki checkpoint lengkap. Berikutnya pengujian state/fungsi/backend/AI, dilanjutkan perbaikan berdasarkan temuan; jangan mengulang inventaris awal atau menganggap snapshot sebagai penerimaan akhir.
