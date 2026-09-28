# WasteNot: Spesifikasi Layar dan Komponen

Versi 1.3 · 28 September 2026 · Core + AI extension · Rancangan implementasi

Lihat [PRD](PRD.md) untuk aturan produk dan [DESIGN](DESIGN.md) untuk styling. ID layar di sini juga dipakai pada [HTML design system](design-system.html). Layar opsi tidak otomatis menjadi scope MVP. Seluruh data visual adalah simulasi berlabel, dengan acuan 21 September 2026.

## 1. Pola bersama

- App bar: judul layar, Back bila bukan tujuan utama, aksi maksimal dua yang benar-benar relevan.
- Navigasi utama: Beranda, Riwayat, Panduan, Statistik, Profil. Setiap ikon memiliki label.
- Scroll vertikal untuk konten yang panjang. CTA form tidak menutupi input atau keyboard.
- Loading awal memakai progress dan teks konteks; refresh mempertahankan data terakhir.
- Empty pertama berbeda dari hasil pencarian/filter kosong. Error jaringan berbeda dari validasi field.
- Semua perubahan data menunggu konfirmasi server; jangan menampilkan keberhasilan hanya karena tombol diketuk.
- Status penyimpanan dan error diumumkan ke layanan aksesibilitas. Fokus kembali ke pemicu dialog ketika dialog ditutup.

## 2. Inventaris layar

| ID | Nama | Prioritas | Tujuan utama | Route/overlay |
|---|---|---|---|---|
| S01 | Pemulihan sesi | Must | Menentukan akar navigasi | Session |
| S02 | Masuk | Must | Memulai sesi | Login |
| S03 | Daftar | Must | Membuat akun | Register |
| S04 | Beranda | Must | Melihat hari ini dan mulai mencatat | Home |
| S05 | Catat aktivitas | Must | Membuat satu catatan valid | LogCreate |
| S06 | Catatan tersimpan | Must feedback | Mengonfirmasi hasil dan langkah berikutnya | Hasil di detail, bukan tab |
| S07 | Riwayat | Must | Menemukan catatan pribadi | History |
| S08 | Detail catatan | Must | Memeriksa dan mengoreksi data | LogDetail(id) |
| S09 | Ubah catatan | Must rancangan | Memperbaiki data | LogEdit(id) |
| S10 | Filter riwayat | Must | Memilih kriteria daftar | Bottom sheet |
| S11 | Panduan | Must | Mencari informasi material | Guide |
| S12 | Detail panduan | Must | Membaca penanganan dan sumber | GuideDetail(id) |
| S13 | Statistik | Must | Menilai rentang 7/30 hari | Statistics |
| S14 | Profil dan progres | Must + baseline XP | Melihat akun dan kebiasaan | Profile |
| S15 | Achievement | Should | Melihat syarat dan progres | Achievements |
| S16 | Pengaturan | Rancangan pendukung | Mengatur tampilan dan preferensi | Settings |
| S17 | Pengingat | Should | Memilih pengingat sukarela | ReminderSettings |
| S18 | Lampiran foto | Stretch | Menambahkan gambar pendukung | Panel editor |
| S19 | Titik daur ulang | Could | Melihat fasilitas terverifikasi | RecyclingPoints |
| S20 | State aplikasi | Dokumentasi | Membandingkan kondisi gagal/kosong/loading | Bukan route produksi tersendiri |
| S21 | Kamera & scan | AI increment | Foto dan scan, fallback manual | Bottom sheet |
| S22 | Konfirmasi objek | AI increment | Satu objek dan koreksi kategori | Fase sheet |
| S23 | Kondisi objek | AI increment | Konteks dari pengguna | Fase sheet |
| S24 | Konfirmasi saran | AI increment | Review metode sebelum jumlah | Fase sheet |
| S25 | Fallback AI | AI increment | Pemulihan dan manual | Fase sheet |

## 3. Detail per layar

### S01: Pemulihan sesi

Tampilkan wordmark teks WasteNot dan “Menyiapkan catatanmu…”. Tidak ada splash berdurasi buatan atau carousel onboarding wajib. Session repository membaca credential, memverifikasi/memperbarui sesi bila perlu, lalu mengganti root menjadi Masuk atau Beranda. Jika tidak ada credential, segera ke Masuk.

Ketika jaringan gagal memvalidasi sesi yang tidak dapat dipastikan: tampilkan “Belum bisa memeriksa sesi”, “Coba lagi”, dan “Masuk ulang”. Jangan memperlihatkan data pribadi dari identitas yang belum dipastikan. Back mengikuti perilaku sistem. HTML menyediakan tombol “Simulasikan sesi valid” karena tidak melakukan autentikasi sebenarnya.

### S02: Masuk

Urutan: wordmark → judul “Catat kebiasaanmu lagi” → email → password dan tampil/sembunyikan → error form → “Masuk” → “Belum punya akun? Daftar”. Keyboard email untuk email, password masking untuk password, IME next/done sesuai urutan. Password manager dan paste diperbolehkan.

Validasi kosong/format dilakukan sebelum request. Credential salah menampilkan pesan umum “Email atau kata sandi belum cocok.” Network error terpisah. Saat submitting, tombol menampilkan “Sedang masuk…” dan dinonaktifkan. Sukses menghapus auth stack. Jika sesi berakhir, banner menjelaskan alasan login ulang. Lupa password tidak ditampilkan sebagai tautan mati; fitur tersebut belum masuk baseline.

### S03: Daftar

Field nama, email, password, konfirmasi password, serta zona waktu awal yang terlihat. Label helper password minimum 12 karakter. Validasi lengkap pada PRD §8. Tombol “Buat akun”, link “Sudah punya akun? Masuk”. Tidak menambahkan centang persetujuan dokumen yang belum dibuat.

Email duplikat terasosiasi dengan field email. Kesalahan validasi tidak menghapus field lain. Password tidak dipersistenkan ketika proses aplikasi dihentikan. Sukses langsung masuk sesi jika API memang menerbitkannya; gunakan root Home. Kebijakan privasi dan persetujuan distribusi publik adalah pekerjaan release tersendiri.

### S04: Beranda

Fokus layar adalah total hari ini dan tindakan pencatatan. Susunan:

1. Sapaan berdasarkan nama pengguna dan tanggal lokal.
2. Ringkasan “Sampah ditangani hari ini” dengan berat; jumlah benda ditangani muncul terpisah jika ada.
3. Reduce terpisah: “2 benda dihindari” pada dataset contoh.
4. Recycling rate dengan keterangan basis aktivitas; contoh hari ini 50% dari 2 aktivitas penanganan.
5. Tombol pendukung “Input manual”; action utama Catat/Scan berada di dock.
6. Progres XP/level ringkas; contoh 50/100 XP di level 1.
7. Tiga aktivitas terbaru, setiap baris membuka detail; “Lihat riwayat” menuju S07.

Dashboard bukan daftar angka prestasi lingkungan. Empty pengguna baru: “Belum ada catatan hari ini”, CTA mencatat, dan akses panduan. Jika ada data kemarin tetapi hari ini kosong, jangan menyembunyikan progres sepanjang waktu. Error full-screen hanya untuk load pertama tanpa cache; gagal refresh memakai banner di atas data lama.

### S05: Catat aktivitas

Form satu halaman scrollable, tanpa wizard. Field berurutan: metode → kategori → pilihan Berat/Jumlah → input nilai dan unit → tanggal aktivitas → catatan opsional → lampiran jika fitur aktif → CTA Simpan.

Metode memakai pilihan tunggal berlabel Reduce, Reuse, Recycle, Trash dengan satu kalimat arti. Kategori memakai menu/pilihan yang menampilkan label penuh. Berat default g; pilihan kg mengubah tampilan saja, payload tetap integer gram. Saat beralih ke Jumlah, field berat dinonaktifkan/dikosongkan dari payload dan label menjadi “Jumlah benda”.

Jika metode Reduce, ubah label menjadi “Jumlah yang dihindari” dan tampilkan “Dicatat terpisah dari sampah yang ditangani.” Pilihan kategori/metode tidak diisi diam-diam pada draf kosong. Prefill dari panduan terlihat dan dapat diganti.

Validasi saat blur dan submit; jangan menampilkan error sebelum pengguna berinteraksi. Submit invalid memfokuskan error pertama. Submit valid membuat satu request dengan client_request_id. Saat hasil request belum diketahui akibat timeout, isian awal dipertahankan untuk retry yang aman. Back pada dirty form membuka D03. Tanggal default hari ini menurut zona akun.

### S06: Catatan tersimpan

Feedback tampil setelah server mengirim ID. Ringkasan kategori, metode, nilai, tanggal, dan perubahan XP. Tombol “Lihat catatan” dan “Kembali ke beranda”. Teks “+10 XP” hanya setelah create baru, bukan replay/edit. Pada Android feedback dapat berupa banner/snackbar di detail S08 agar tidak menambah route yang tidak perlu. Di HTML ditampilkan tersendiri untuk memperjelas hasil transaksi.

Jangan memakai konfeti berulang, suara otomatis, atau klaim “menyelamatkan bumi”. TalkBack mendengar “Catatan tersimpan”. Feedback tidak otomatis hilang sebelum pengguna sempat membacanya bila memuat aksi penting.

### S07: Riwayat

Header Riwayat, filter, chip kriteria aktif, daftar kelompok tanggal menurun, serta tombol pendukung “Input manual”. Setiap baris berisi ikon kategori, kategori, metode, tanggal/catatan ringkas, dan nilai beserta unit. Reduce diberi label “dihindari”.

Baris membuka S08. Pagination bertahap 20 catatan; loading/error halaman lanjutan berada di bawah daftar. Filter menggabungkan kategori, metode, dan tanggal. Scroll dan filter bertahan saat detail dibuka lalu ditutup. Empty awal mengajak mencatat; filtered-empty menawarkan reset. Penghapusan menghapus item hanya setelah 204, lalu merefresh agregat.

### S08: Detail catatan

Judul kategori, metode, nilai besar, tanggal aktivitas dan zona, catatan atau “Tanpa catatan”, foto jika ada, serta waktu dibuat/diperbarui pada bagian sekunder. Tombol “Ubah catatan” menuju S09, aksi “Hapus catatan” membuka D01. Tidak ada share publik.

Deleted/404 menunjukkan “Catatan tidak tersedia” dan kembali ke Riwayat. Tidak membedakan catatan milik orang lain. Jika detail lama tersimpan di cache, jangan izinkan edit/hapus tanpa version yang valid. Foto private kedaluwarsa dimuat ulang lewat endpoint terautentikasi, bukan mengganti menjadi public URL.

### S09: Ubah catatan

Menggunakan komponen editor S05 dengan data awal dan version. Judul “Ubah catatan”, CTA “Simpan perubahan”. Tambahkan catatan bantuan “Perubahan akan memperbarui statistik. Mengubah catatan tidak menambah XP.” Date masih mengikuti jendela validasi; jika catatan lama berada di luar jendela, mempertahankan activity_date lama diperbolehkan, tetapi tanggal baru harus lolos aturan create.

Jika tidak berubah, CTA boleh disabled. Konflik version membuka D04; draf tidak hilang. Sukses kembali ke detail terbaru. Load gagal sebelum form tersedia tidak menampilkan nilai kosong sebagai nilai catatan sebenarnya.

### S10: Filter riwayat

Modal bottom sheet berjudul “Filter riwayat”, kategori tunggal (Semua + 7 kategori), metode tunggal (Semua + 4 metode), tanggal mulai/akhir, “Reset”, dan “Terapkan filter”. State edit di sheet terpisah dari filter aktif sampai Apply. Cancel/Back/dismiss tidak menerapkan perubahan.

Rentang awal tidak boleh setelah akhir. Tanpa salah satu tanggal, aplikasi mengosongkan keduanya atau meminta pasangan lengkap, sesuai validasi field yang terlihat. Reset langsung mengosongkan draft filter; Apply menutup sheet dan fetch ulang dari halaman pertama. Sheet scrollable pada font scale besar, fokus dibatasi pada modal saat terbuka.

### S11: Panduan

Search field “Cari botol, kardus, atau material lain”, kategori opsional, daftar judul/item dengan metode rekomendasi dan ringkasan pendek. Debounce usulan 300 ms, cancel request lama; tombol clear memiliki contentDescription. Search q maksimum 100 karakter.

Hasil kosong: “Panduan belum ditemukan. Coba nama material yang lebih umum.” dengan “Hapus pencarian”. Error jaringan: “Panduan belum bisa dimuat” dan retry. Jika artikel contoh belum direview, HTML memberi label draf; produksi tidak memuat artikel DRAFT.

### S12: Detail panduan

Susunan: judul item → kategori/metode → ringkasan → langkah bernomor → hal yang perlu diperhatikan → sumber dan tanggal review → “Catat aktivitas ini”. Tautan sumber membuka browser, dengan host yang jelas. Konten tidak mengklaim semua fasilitas menerima material yang sama.

CTA membuka S05 dengan kategori/metode awal, tanpa membuat catatan otomatis. Artikel archived/404 memiliki jalan kembali. Jika sumber/tanggal review belum ada, layar prototype menyatakan “Contoh editorial; belum diverifikasi” dan tidak mengarang tautan atau tanggal.

### S13: Statistik

Toggle “7 hari” / “30 hari”, rentang tanggal eksplisit, ringkasan berat/jumlah ditangani dan Reduce terpisah, grafik harian dengan label unit, daftar kategori, serta distribusi metode empat baris. Tampilkan recycling rate berbasis aktivitas dengan numerator/denominator.

Dataset acuan 7 hari menghasilkan 1,4 kg, 5 aktivitas, 2 benda dihindari, recycling rate 50%. Chart g tidak menggabungkan count; gunakan selector/seri terpisah jika data COUNT penanganan tersedia. Hari tanpa data bernilai 0, bukan tidak ditampilkan. Semua chart punya uraian teks atau tabel. Rentang kosong: “Belum ada catatan pada periode ini”, pilihan periode lain dan Catat aktivitas.

Saat range berganti, loading hanya bagian statistik dan respons lama tidak menimpa range baru. Jika request gagal, range data lama tetap dilabeli dengan benar; jangan memasang judul 30 hari di atas angka 7 hari.

### S14: Profil dan progres

Nama, email, timezone, level, XP bar, aktivitas sepanjang waktu, streak jika diaktifkan, link achievement, pengaturan, dan “Keluar”. Avatar awal menggunakan inisial, bukan foto orang fiktif. Nama/email contoh di mockup diberi identitas “Pengguna Demo”.

Progress bar di level 1 contoh 50/100, label “50 XP lagi ke level 2”. Streak menerangkan hari, bukan jumlah catatan. Belum mencatat: level 1, 0 XP, CTA mencatat. Profile fetch error tidak menampilkan data akun lama. Keluar membuka D02.

### S15: Achievement (Should)

List judul, syarat, progres numerik dan bar, status terkunci/tercapai. Tidak mengandalkan opacity rendah untuk teks terkunci. Detail syarat dapat dibuka melalui accordion. Contoh: Langkah pertama 1/1 tercapai; Mulai terbiasa 5/5 tercapai; Pilah untuk daur ulang 2/3; Satu minggu konsisten 2/7.

Status dihitung dari data aktif, sehingga hapus dapat mengunci kembali. Bila capability mati, link layar disembunyikan pada aplikasi. HTML tetap menyertakan desain dengan penanda “Should”.

### S16: Pengaturan

Pilihan tema Sistem/Terang/Gelap; link pengingat bila fitur aktif; informasi versi aplikasi; ringkasan privasi yang menjelaskan data pribadi serta akses foto opsional. Pengaturan theme disimpan lokal dan berlaku segera. Zona akun ditampilkan read-only di baseline; jangan menyediakan editor yang belum didukung API.

Tautan privasi produksi hanya tersedia setelah dokumen benar-benar disediakan. HTML menampilkan penjelasan cakupan draft tanpa mengklaim sebagai kebijakan resmi.

### S17: Pengingat (Should)

Switch default off, waktu “Sekitar 20.00” yang dapat diubah, kalimat “Jadwal dapat terlambat mengikuti kondisi perangkat.” dan tombol “Simpan pengingat”. Permission notifikasi diminta setelah intent mengaktifkan. Permission denied memunculkan penjelasan dan akses pengaturan sistem; bukan prompt berulang.

Saat off, waktu disabled atau tetap terlihat sebagai preferensi tanpa jadwal aktif. Logout membatalkan reminder akun. HTML hanya menyimulasikan preference, tidak meminta izin browser atau mengirim notifikasi nyata.

### S18: Foto (Stretch)

Panel editor dengan “Pilih foto”, preview, nama file/ukuran, “Ganti”, “Hapus foto”, status upload, dan “Lanjut tanpa foto”. Gunakan Photo Picker Android. Foto tidak menjadi input wajib. Jika tipe/ukuran salah, error menempel pada panel dan field catatan tetap tersedia.

HTML memakai preview gambar lokal tanpa upload. Label menjelaskan ini simulasi. Tidak menambahkan foto stok yang seolah merupakan foto pengguna, tidak meminta akses kamera tanpa tindakan eksplisit.

### S19: Titik daur ulang (Could)

Desain memuat judul, “Gunakan lokasiku”, pencarian area, daftar fasilitas, dan slot peta. Karena belum ada dataset lokasi, default mockup adalah “Data titik daur ulang belum tersedia” dengan penjelasan bahwa fitur memerlukan lokasi terverifikasi. Tidak ada pin atau jarak palsu.

Saat diimplementasikan: bedakan loading, permission denied, location unavailable, hasil kosong, provider error, dan data kadaluwarsa. Lokasi device opsional; daftar manual tetap tersedia. Detail titik memuat nama, alamat, material diterima, jam jika ada sumber, tanggal verifikasi, dan action navigasi eksternal. Tidak menjamin operasional fasilitas tanpa verifikasi.

### S20: Galeri state

Panel dokumentasi yang memungkinkan reviewer memilih loading, empty, filtered-empty, error, offline, session-expired, dan success. Ini bukan menu yang dikirim ke end user Android. State dipakai ulang pada layar terkait, dengan teks spesifik konteks.

| State | Contoh teks | Aksi |
|---|---|---|
| Loading | “Memuat riwayat…” | Menunggu; konten tidak actionable |
| Empty awal | “Belum ada catatan. Mulai dari aktivitas hari ini.” | Catat aktivitas |
| Filter kosong | “Tidak ada catatan yang cocok dengan filter ini.” | Reset filter |
| Error | “Riwayat belum bisa dimuat.” | Coba lagi |
| Offline | “Koneksi terputus. Isianmu tetap tersedia.” | Coba simpan lagi |
| Session expired | “Sesi berakhir. Masuk lagi untuk membuka catatanmu.” | Masuk |
| Success | “Catatan tersimpan.” | Lihat catatan |

## 4. Dialog dan sheet

| ID | Pemicu | Isi dan aksi | Cancel / Back |
|---|---|---|---|
| D01 Hapus catatan | S08 | Sebut kategori/tanggal; “Statistik dan XP akan dihitung ulang.”; tombol Hapus (destructive) | Batal, tidak mengubah data |
| D02 Keluar | S14 | “Kamu perlu masuk lagi untuk membuka catatan.”; Keluar | Batal |
| D03 Buang draf | Editor dirty + Back | “Perubahan belum disimpan.”; Buang perubahan | Lanjut mengisi |
| D04 Konflik | PATCH/DELETE 409 | “Catatan telah berubah. Muat versi terbaru sebelum mencoba lagi.”; Muat versi terbaru | Tetap di draf; tidak overwrite |

Tombol destructive memakai warna error, bukan warna CTA biasa. Request active menonaktifkan konfirmasi berulang. Error server tampil di dialog atau halaman, tidak menutup dialog lalu seolah berhasil. Keyboard Escape pada HTML setara cancel. Fokus kembali ke pemicu setelah cancel.

## 5. Katalog komponen dan pemetaan Compose

| ID | Komponen | Properti penting | Variasi/state | Compose |
|---|---|---|---|---|
| C01 | Wordmark | text | Terang/gelap | Text + Plus Jakarta Sans ExtraBold |
| C02 | App bar | title, back, actions | Root/detail | TopAppBar |
| C03 | Primary button | label, enabled, loading | Default/pressed/focus/disabled/loading | Button |
| C04 | Secondary button | label | Default/disabled/focus | OutlinedButton |
| C05 | Text action | label | Normal/destructive | TextButton |
| C06 | Icon button | icon, contentDescription | Selected/disabled | IconButton |
| C07 | Input manual | label | Buka S05 | OutlinedButton pendukung; C35 untuk scan |
| C08 | Text field | value, label, helper, error | Empty/filled/focus/error/disabled | OutlinedTextField |
| C09 | Password field | value, visibility | Masked/visible/error | TextField + visual transformation |
| C10 | Search | query, onClear | Empty/query/no result | OutlinedTextField |
| C11 | Quantity field | mode, unit, rawText, error | g/kg/benda; Reduce helper | TextField + single-choice unit |
| C12 | Category picker | selected, options | Default/selected/error | ExposedDropdownMenuBox atau sheet |
| C13 | Method selector | method | Four single-choice methods | RadioButton/list pilihan |
| C14 | Filter chip | label, selected, onRemove | Selected/unselected/disabled | FilterChip/InputChip |
| C15 | Range selector | 7d/30d | Selected/loading | SingleChoiceSegmentedButtonRow |
| C16 | Method badge | method, label | Reduce/Reuse/Recycle/Trash | Surface + Text; bukan action |
| C17 | Waste log row | log, onClick | Default/pressed | ListItem / Surface |
| C18 | Summary surface | label, value, unit, caption | Zero/value/unknown | Surface + Text |
| C19 | XP progress | current, target, level | Zero/partial/full | LinearProgressIndicator |
| C20 | Achievement row | title, current, target | Locked/unlocked | ListItem + ProgressIndicator |
| C21 | Guide row | title, category, method | Default/pressed | ListItem |
| C22 | Bar chart | dailyValues, unit, labels | Empty/data | Canvas + semantics + teks alternatif |
| C23 | Method breakdown | four counts, total | Empty/data | Rows + progress + label |
| C24 | Bottom navigation | route, destinations | Selected/unselected | NavigationBar |
| C25 | Navigation rail | route, destinations | Medium/expanded | NavigationRail |
| C26 | Modal sheet | title, content, actions | Open/closed/scroll | ModalBottomSheet |
| C27 | Confirmation | title, body, primary, cancel | Default/destructive/busy/error | AlertDialog |
| C28 | Snackbar/banner | message, action | Success/error/offline | SnackbarHost / Surface |
| C29 | Async state | mode, message, onRetry | Loading/empty/error | Shared composable |
| C30 | Reminder switch | checked, onChange | Off/on/denied | Switch |
| C31 | Date/time input | value, range | Valid/invalid/disabled | DatePicker / TimePicker |
| C32 | Photo attachment | uri, status | Empty/preview/upload/error | Photo Picker + image surface |
| C33 | Source link | title, URL | Reviewed/draft | TextButton/open URI |
| C34 | Category icon | category, description | Seven categories | VectorDrawable / Icon |

Bentuk komponen harus dipetakan ke token, bukan menyalin CSS px secara buta ke dp/sp. Komponen yang hanya tampil satu kali boleh tetap berada dalam screen; katalog adalah bahasa desain, bukan instruksi membuat 34 wrapper Kotlin.

## 6. Matriks state wajib

| Layar | Loading | Empty | Content | Error | Kasus tambahan |
|---|---|---|---|---|---|
| S04 Beranda | Load awal / refresh | Hari ini kosong | Ringkasan + list | Retry / banner | Persentase null |
| S07 Riwayat | Awal / halaman berikut | Baru / filter | Kelompok tanggal | Awal / load more | Scroll/filter dipertahankan |
| S08 Detail | Fetch | 404 terarah | Record | Retry | Deleted / stale version |
| S11 Panduan | Search/list | Tidak ada hasil | Artikel | Retry | Respons pencarian lama |
| S12 Detail panduan | Fetch | Artikel tidak tersedia | Langkah/sumber | Retry | Draft tidak dipublikasikan |
| S13 Statistik | Load/range | Rentang kosong | Agregat | Retry | Campuran unit / Reduce-only |
| S14 Profil | Fetch | Progres 0 | Akun/progres | Retry | Sesi berakhir |
| S15 Achievement | Fetch | Definisi belum tersedia | Progress | Retry | Capability disabled |
| S05/S09 Form | Submit | Form awal, bukan empty page | Isian | Inline/server | Dirty, timeout, conflict |

## 7. Handoff dan evaluasi desain

Reviewer memeriksa setiap ID di HTML, lalu mencocokkan rule produk dengan PRD/API. Perbedaan HTML dan Android yang sengaja ada: HTML memakai browser form controls, dialog native, serta data simulasi; Android memakai komponen Compose dan API nyata. HTML tidak membuktikan autentikasi, upload privat, scheduling Android, server ownership, atau performa perangkat.

Checklist: seluruh field memiliki label; setiap action memiliki destination/feedback; warna tidak menjadi satu-satunya status; teks error dapat dibaca; font besar tidak menutupi CTA; chart menjelaskan unit; source data dan opsi fitur terlihat; semua sublayar dapat kembali ke asal tanpa kehilangan filter/draf yang memang harus dipertahankan.

## 8. AI camera bottom sheet, S21–S25

Revisi 28 September menambahkan action Catat/Scan pada tengah atas dock, sambil mempertahankan lima tab. Ini tombol aksi, bukan tab keenam dan tidak memakai aria-current. Sheet opak, sudut atas 28, drag handle dekoratif, close ≥48 dp, judul dan fallback manual. Kamera hanya diaktifkan setelah aksi/izin. Android boleh langsung meminta izin ketika action scan diketuk; HTML menyediakan tombol Aktifkan kamera agar demo tidak memicu permission tanpa pilihan. Close/Escape/Back menghentikan kamera dan mengembalikan fokus ke pemicu.

| ID | Panel / fokus | Field / aksi | Validasi & state |
|---|---|---|---|
| S21 | Kamera dan tinjau foto | Preview, aktifkan kamera, ambil foto, tinjau, scan, retake, manual | Permission denied, unavailable, capture belum siap; kirim hanya setelah konfirmasi |
| S22 | Deteksi / konfirmasi satu objek | Bbox + daftar, skor, pemilihan objek, koreksi kategori, konfirmasi | Empty, low confidence, material unknown, multi-object; kategori tidak boleh kosong |
| S23 | Kondisi objek | Cleanliness, integrity, reusable, reuse intention | UNKNOWN valid; field independent; perubahan membatalkan saran lama |
| S24 | Rekomendasi | Metode, alasan, sumber/versi panduan, caution, ubah/gunakan | Tidak auto-save; special handling method null; lanjut S05 jumlah manual |
| S25 | Gagal / tidak tersedia | Penyebab, ulang, manual, close | Kamera denied, unknown, offline, timeout, invalid LLM, no reviewed guide; tidak menambah XP |

S21–S25 adalah fase satu alur sheet dalam produk; ditampilkan sebagai lima entry galeri untuk handoff. Preview HTML memakai ilustrasi bbox berlabel, bukan bbox terhadap foto kamera. Skor/objek/LLM adalah fixture. Kamera browser menggunakan getUserMedia jika didukung konteks aman; tidak mengunggah gambar. Pengujian kamera Android dan API tetap terpisah.

S05 sesudah AI: prefill hanya kategori dan metode yang dikonfirmasi; jumlah kosong, unit default g; tanggal tetap aturan core. Pengguna dapat mengoreksi. Membatalkan scan tidak menciptakan log. Dirty draft existing harus dipertahankan atau meminta D03 sebelum diganti; jangan memaksa navigate dan membuang isian. Panduan default belum terverifikasi pada mockup tidak menjadi knowledge produksi.

| Komponen tambahan | Padanan Compose | Perilaku |
|---|---|---|
| C35 Scan action | Button / FAB | Lime, ikon kamera + label Catat/Scan, sentuh ≥48 dp |
| C36 Camera sheet | ModalBottomSheet + camera preview | Height adaptif, scroll, izin, capture/retake, manual fallback |
| C37 Detection review | Canvas overlay + selectable list | Bbox sesuai image transform; label accessible menyebut objek dan skor |
| C38 Object context | Select/radio controls | Tiga dimensi terpisah dan unknown; tidak menyimpulkan kondisi dari gambar |
| C39 Recommendation review | Surface + method selection | Sumber, reason, caution; konfirmasi; error dan special handling punya manual |

State prototipe bisa dipilih pada S21: satu objek, beberapa objek, skor rendah, tidak dikenali, penanganan khusus, jaringan gagal, rekomendasi gagal. Semua tersedia tanpa hardware kamera melalui “Gunakan contoh simulasi”. Tombol ini hanya untuk dokumentasi, tidak muncul sebagai fitur produksi. Refresh menghapus foto dan state sementara.

## 9. Penyempurnaan mockup v1.3

S21–S25 memakai isi komponen sheet yang sama, dengan field/aksi nyata sebagai preview inert. Buka “Coba bottom sheet” untuk interaksi. Tahap Foto → Objek → Kondisi → Saran mempunyai indikator current step. Pengaturan skenario simulasi berada pada disclosure agar jalur kamera lebih mudah dibaca. Input manual berada di footer opak yang menempel bagian bawah sheet.

Kategori/objek/kondisi/niat penggunaan ulang tetap tersimpan ketika kembali untuk koreksi. Mengganti kategori mereset kondisi agar tidak memakai konteks benda lama. Memilih contoh simulasi menghentikan kamera aktif dan membuang hasil permission yang terlambat. Scan baru dari katalog ditolak dengan pesan saat editor mempunyai draf dirty; pengguna menyimpan atau kembali melalui D03 terlebih dahulu. Kamera/LLM tetap simulasi sebagaimana v1.2.
