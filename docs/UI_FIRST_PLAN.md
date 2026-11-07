# Prioritas UI WasteNot

Arahan pengguna, ditegaskan kembali 7 November 2026: lengkapi screen yang dibutuhkan, cocokkan dengan design system/mockup dan navigasinya, lalu lakukan pengujian menyeluruh. Integrasi yang sudah tersedia dipertahankan. Build dasar dan pemeriksaan visual selama implementasi membantu memastikan layar dapat dibuka; pengujian backend, lintas perangkat dan regresi mendalam dilakukan setelah rangkaian UI lengkap.

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

Inventaris struktur dan snapshot awal sudah lengkap. Prioritas berikutnya adalah menuntaskan review dan koreksi visual seluruh alur sesuai kit, termasuk bagian scroll serta scan tema gelap yang belum memiliki pasangan gambar. Setelah checkpoint visual lengkap, lanjutkan pengujian state/fungsi/backend/AI. Snapshot awal tidak menjadi penerimaan akhir.

7 November: [jalur data Android ke AI nyata](qa/iteration-2/AI_LIVE_ANDROID.md) lulus pada API 37, termasuk konteks tambahan, Gemini non-prototipe, provenance, pemulihan draf dan replay tanpa XP ganda. [Alur kamera/UI nyata](qa/iteration-2/AI_LIVE_UI.md) kemudian juga lulus sampai simpan, edit, hapus dan keluar, dengan 15 screenshot serta cleanup akun uji terverifikasi. Kesehatan SystemUI emulator dan ketepatan frame screenshot diperiksa agar gangguan alat tidak dilaporkan sebagai hasil aplikasi. Konfigurasi publikasi juga diperbarui untuk memakai adapter remote dan menolak origin API kosong.


## Goal dan checkpoint yang berlaku, 7 November 2026

**Goal utama:** memastikan semua screen wajib WasteNot Android tersedia, lalu menuntaskan tampilan dan navigasinya sesuai design system serta mockup. Setelah checkpoint UI lengkap, jalankan pengujian menyeluruh dan perbaiki temuan sampai APK siap ditinjau.

Urutan ini menggantikan prioritas pengujian integrasi panjang pada catatan sebelumnya. Hasil integrasi yang sudah ada tetap dicatat pada commit buktinya.

| Tahap | Kriteria selesai | Posisi saat ini |
|---|---|---|
| 1. Kelengkapan screen | Seluruh 20 ID wajib dan 4 dialog tersedia; placeholder dan aksi tanpa tujuan dicatat | Implementasi dan snapshot awal 20/20 + 4/4 tersedia; tidak perlu membuat ulang inventaris |
| 2. Kesesuaian desain dan navigasi | Review setiap alur dari atas sampai bawah, tema terang/gelap, komponen kit, bottom nav, Back serta buka/tutup modal; selesaikan koreksi yang ditemukan | Berjalan; pasangan scan dua tema dan akhir scroll tersedia; state/kontrol serta perjalanan UI nyata masih ditinjau |
| 3. Pengujian menyeluruh | Fungsi, backend/AI nyata, pemulihan draf, perangkat Android baru, konfigurasi/font, aksesibilitas serta CI | Dilanjutkan setelah tahap 2; bukti parsial yang ada tetap sah dalam batas masing-masing |

Pekerjaan terdekat tahap 2:

1. Tinjau pasangan kit dan aplikasi per alur: masuk/daftar, beranda/catat/riwayat, panduan, statistik/profil/pengaturan, lalu scan sampai editor.
2. Periksa bagian bawah konten, state loading/kosong/gagal dan dialog; tandai temuan pada screen terkait agar koreksinya terbatas dan dapat ditinjau.
3. Lengkapi preview scan terang/gelap dengan data demo terkontrol. Pemeriksaan visual tidak harus menunggu deteksi positif kamera emulator atau respons Gemini.
4. Tinjau bottom navigation, header, tombol utama, sheet dan transisi secara konsisten; siapkan APK serta galeri pembanding untuk checkpoint UI.

Selama tahap 1–2, build, pemeriksaan render dan pemeriksaan wajib commit/CI tetap berjalan agar perubahan tidak rusak. Jangan menambah putaran pengujian integrasi/perangkat yang panjang sebelum checkpoint UI lengkap, kecuali diperlukan untuk membuka atau memperbaiki layar yang sedang dikerjakan.

Laporan kemajuan dipisahkan menjadi **screen tersedia**, **review desain selesai**, dan **pengujian selesai**. Hanya hitungan dengan daftar bukti yang dipakai; 20/20 screen tersedia tidak berarti desain atau rilis 100% selesai. Fitur S15/S17/S18/S19 tetap mengikuti prioritas dan capability pada spesifikasi, bukan otomatis ditambahkan ke cakupan wajib.

Penegasan goal terbaru, 7 November: utamakan penyelesaian screen dan integrasi backend/AI, dengan pengujian utama pada perangkat Android baru (checkpoint saat ini API 37). Pengujian perangkat lama tidak menjadi pekerjaan yang diperluas. Batas instalasi Android 11 yang sudah dipilih tetap berlaku; ini bukan kewajiban menambah matriks pengujian perangkat lama.

Checkpoint visual 7 November: [scan dua tema](qa/iteration-2/SCAN_THEMES.md) melengkapi kamera sebelum aktivasi, foto dan S22–S24 hingga akhir scroll, 40 PNG pada API 37. Pilihan scan sudah minimum 56 dp dan preview native lulus. Celah pasangan tema normal tersebut tertutup; state kamera aktif, pertanyaan tambahan/bersyarat dan interaksi kontrol tetap perlu ditinjau sebelum penerimaan seluruh UI.

Checkpoint review berikutnya: [versi panduan dan fallback expiry](qa/iteration-2/SCAN_REVIEW_FIXES.md) diperbaiki sesudah dua review independen, dengan 24 unit tests dan dua metode UI pada perangkat API37 lulus. Percobaan awal dengan fixture kamera terpotong tidak dihitung sebagai saran AI berhasil. Setelah pembingkaian fixture diperbaiki, [jalur AI positif melalui CameraX](qa/iteration-2/AI_LIVE_UI.md) lulus dengan deteksi, pertanyaan tambahan dan saran Gemini nyata; ini bukan penerimaan akurasi model atau kamera fisik.

Penyesuaian dokumentasi backend/AI terbaru: [streak server dan label deteksi](qa/iteration-2/BACKEND_ANDROID_ALIGNMENT.md), dua metode UI API37 lulus dan enam gambar. S14 membedakan nilai nol dari nilai yang belum tersedia; S22 tetap menampilkan label mentah ketika katalog tidak memberi nama terjemahan, tanpa memilih material otomatis. Kedua bundle kontrak tetap cocok, masing-masing 34 artifact. Pengujian akhir seluruh UI dan CI tetap memiliki status terpisah dari checkpoint ini.

Checkpoint interaksi 7 November: [outline fokus tombol dan tab](qa/iteration-2/KEYBOARD_FOCUS.md) diselaraskan dengan kit setelah review frame keyboard. Primary/secondary/text/icon, Catat/Scan dan navbar memiliki penanda fokus; Tab tidak langsung memilih rute, Enter memilih, dan menutup scan mengembalikan fokus ke pemicu. Secondary fokus juga memakai primary-soft dan outline primary. Empat metode native lulus pada API37; 14 frame sebelum dan14 sesudah menunjukkan bounds tetap serta stroke sesuai token. Ini menutup bagian interaksi tersebut pada ponsel normal dua tema, bukan seluruh state/font/input atau penerimaan global.
