# Iteration 2: kesesuaian desain Android

Dimulai 19 Oktober 2026, dari `main` pada `ceb8707`. Status: **dalam pengerjaan**. Iteration 1 membuktikan fondasi fungsi; penerimaan visualnya belum terpenuhi.

## Sasaran dan sumber

Implementasi mengikuti `docs/WasteNot-Product-Design-Kit update/WasteNot/DESIGN.md`, `SCREEN_SPEC.md`, serta render `design-system.html` v1.3. `preview-glass.png` yang lebih lama belum memuat Catat/Scan, sehingga tidak cukup untuk menilai navbar v1.3.

Permintaan pengguna mengubah sumber data: hapus mode demo dan tombol masuk contoh. Gunakan dummy sebagai adapter aplikasi yang dapat digunakan tanpa menunggu backend. Adapter HTTP tetap terpisah untuk integrasi berikutnya. Dummy tidak membuktikan autentikasi server, penyimpanan cloud, inference, atau penerbitan artikel yang sudah ditinjau.

Pilihan implementasi: ENERGY 2 / RHYTHM 2 / MOTION 1. Pinus memberi fokus pada angka ringkasan; lime menandai Reduce dan tindakan/tujuan aktif. Font dan ikon berasal dari kit. Daftar aktivitas memakai baris berikon dengan divider. Dock mengikuti radius 26 dp, indikator lime, dan aksi kamera di tengah. Baseline memakai surface opak sebagaimana diizinkan DESIGN §2; blur bukan syarat baseline. Fade 180 ms menjelaskan perpindahan tanpa gerak dekoratif.

## Sumber data dummy

- `AppContainer` memilih dummy secara default untuk semua build type. `-Pwastenot.useDummyData=false` memilih adapter remote; integrasi write/agregat remote masih pekerjaan tersendiri.
- Sesi awal fixture langsung membuka Beranda. Setelah Keluar, Masuk/Daftar tetap melalui validasi form. Password tidak disimpan dan tidak dikirim ke layanan luar.
- Lima catatan awal mengikuti kuantitas PRD §9.4, dengan tanggal hari lokal ketika akun pertama kali mendapatkan seed. Tanggal dan catatan disimpan, bukan digeser setiap aplikasi dibuka.
- Catatan dan idempotency key disimpan per identitas fixture. Menutup aplikasi atau membuang ViewModel tidak menghapus catatan. Ganti akun menggunakan namespace berbeda.
- Write baru berhasil setelah penyimpanan lokal mengonfirmasi commit. Data rusak menghasilkan error penyimpanan, tidak diam-diam diganti dengan seed sukses.

## Matriks penyelesaian

| Area | Persyaratan akhir | Status checkpoint |
|---|---|---|
| S01–S03 | Startup, Masuk/Daftar sesuai kit; tidak ada jalur demo | Preview dihapus dari UI; dummy default; visual auth perlu dibandingkan |
| C24/C25/C35 | Lima tujuan, dock/rail, lime aktif, Catat/Scan, inset, fokus, teks 200% | Dock/rail baru dan journey 360/600 dp normal/200% diperiksa; alur kamera belum lengkap |
| S04 | Nama/tanggal, summary, manual, Reduce, rate, XP, tiga aktivitas | Komposisi baru dan screenshot terang/gelap diperiksa; penyempurnaan detail visual masih berlanjut |
| S05/S06/S08/S09 | Form, hasil simpan, detail, edit/hapus, metadata dan dialog | S08 baca per ID, cache read-only/error/missing, metadata dan D01 hapus/version/agregat lokal tersedia; [QA detail](../qa/iteration-2/DETAIL.md). S06 receipt create/replay dan hasil tersimpan diuji; [QA feedback](../qa/iteration-2/SAVE.md). S05/S09 kini memakai radio, field opak/label atas, jumlah/satuan adaptif, copy kit, blur/fokus, dan kalender tanggal lama; 35 eksekusi native pada 360/600 dp normal/200%, [QA editor](../qa/iteration-2/EDITOR.md). Snapshot/pemulihan POST belum pasti, konflik D04, audit IME editor, spoken feedback dan process death belum selesai |
| S07/S10 | Kelompok tanggal, filter sheet draft/apply/cancel, rentang tanggal | Kelompok tanggal, chip, filter sheet draft/Apply/Batal/tanggal, pemulihan scroll dan viewport 20 item diuji; cursor repository/pull-to-refresh/error halaman lanjut belum selesai |
| S11/S12 | Pencarian, daftar/detail panduan, sumber, prefill editor | Placeholder lama belum diganti |
| S13 | 7/30 hari, summary, grafik harian dan alternatif teks, breakdown | Summary diperbarui; grafik/breakdown belum selesai |
| S14–S16 | Profil/progres, achievement, pengaturan dan Back | Belum selesai secara visual dan cakupan layar |
| S17–S19 | Pengingat/lampiran/lokasi mengikuti capability dan state jujur | Should/Stretch/Could mengikuti kit; jangan mengarang layanan siap |
| S20 | Loading/empty/error/success dipakai ulang, bukan menu produk | Perlu audit pada semua layar yang selesai |
| S21–S25 | Foto/objek/kondisi/saran/fallback, konfirmasi, tidak auto-save | Dock membuka sheet dan manual; foto/AI belum selesai |
| Navigasi | Stack/scroll/draf, Back konsisten, transisi pendek | Fade 180 ms, stack per tab, Back detail dan tujuan daftar Riwayat setelah delete diuji; audit gerak per frame dan cakupan seluruh layar belum selesai |
| QA | Screenshot tiap layar; terang/gelap, teks 200%, ponsel/rail | [Dock/Beranda/dummy](../qa/iteration-2/README.md), [Riwayat/filter](../qa/iteration-2/HISTORY.md), [Detail/hapus](../qa/iteration-2/DETAIL.md), [Feedback simpan](../qa/iteration-2/SAVE.md) dan [Editor](../qa/iteration-2/EDITOR.md); belum semua layar sesuai dan audit material komponen global masih berlanjut |
| Git/CI | Branch `codex/`, commit terarah, hook dan Actions diperiksa | Branch `codex/android-iteration-2-design` |

Status tidak boleh dinaikkan menjadi selesai hanya karena kompilasi atau tes navigasi lulus. Setiap baris memerlukan pemeriksaan sesuai cakupannya. Bukti checkpoint menyebut layar dan konfigurasi yang benar-benar diuji serta pekerjaan yang belum selesai.
