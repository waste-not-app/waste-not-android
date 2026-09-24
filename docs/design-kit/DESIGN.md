# WasteNot Design System

Versi 1.1 · 23 September 2026 · Rancangan untuk Android Jetpack Compose

**Arah:** catatan kebiasaan yang segar, praktis, dan mudah dibaca. Desain ini ditentukan khusus untuk personal waste tracking dan panduan 3R, sesuai permintaan pengguna. Referensi Titip.in tidak menjadi sumber warna, font, bentuk komponen, atau komposisi WasteNot.

Dokumen ini adalah sumber styling untuk [mockup HTML](design-system.html), [spesifikasi layar](SCREEN_SPEC.md), dan implementasi Android. Aturan jumlah, waktu, Reduce, dan XP mengikuti [PRD](PRD.md). Tampilan HTML berisi data simulasi; tidak membuktikan backend atau aplikasi Android sudah tersedia.

## 1. Identitas dan keputusan desain

WasteNot dipakai berkali-kali untuk tugas singkat: mencatat aktivitas, memeriksa riwayat, mencari penanganan, dan memahami progres. Pengalaman harus terasa bersahabat tanpa menyerupai permainan anak, kampanye pemasaran, atau alat penilaian moral.

| Keputusan | Pilihan WasteNot | Alasan |
|---|---|---|
| Karakter | Segar, langsung, mendukung | Pengguna perlu tindakan yang jelas dan tidak dihakimi |
| Warna aksi | Hijau pinus | Membedakan tindakan penting dengan kontras yang kuat |
| Warna progres | Lime | Menandai perkembangan kebiasaan dengan energi yang cukup |
| Latar | Putih kehijauan | Menjaga formulir dan angka dominan, nyaman untuk penggunaan berulang |
| Font | Plus Jakarta Sans | Satu keluarga sans yang ramah, mendukung hierarki jelas dan label UI padat |
| Komposisi | Satu ringkasan utama, lalu daftar | Pengguna membaca satu keputusan dulu, bukan empat angka yang bersaing |
| Bentuk | Sudut 12–24 dp sesuai fungsi | Lembut pada area utama, tetap tegas pada input dan daftar |
| Identitas berulang | Angka tebal, label satuan, bidang progres lime | Menekankan aktivitas dan kebiasaan yang dicatat |
| Ikon | Simbol material dan tindakan yang sederhana | Mempercepat pencarian kategori tanpa memerlukan foto |
| Bahasa | Indonesia, ringkas, tidak menghakimi | Trash adalah metode pencatatan, bukan label pengguna yang buruk |
| Gerak | Transisi pendek atas tindakan | Membantu memahami perubahan state tanpa mengganggu tugas |

Design read: aplikasi pencatatan pribadi untuk mahasiswa/pengguna muda, bergaya utilitarian yang ramah, **ENERGY 2 / RHYTHM 2 / MOTION 1**. Susunan stabil di dalam aplikasi; katalog desain memakai variasi seperlunya untuk membandingkan token, komponen, dan layar.

Tidak memakai serif editorial, terracotta, kartu promosi marketplace, latar dekoratif berpola, ilustrasi stok, atau foto orang fiktif. Wordmark adalah teks WasteNot, belum merupakan logo final yang didaftarkan. Ikon kategori adalah aset UI fungsional, bukan lambang sertifikasi lingkungan.

## 2. Warna semantik

Gunakan nama peran di komponen. Nilai hex hanya didefinisikan dalam theme. Lime tidak digunakan sebagai teks kecil di atas putih.

| Token / CSS | Terang | Gelap | Material 3 / fungsi |
|---|---|---|---|
| background / `--bg` | #F4F7F2 | #101B15 | background |
| surface / `--surface` | #FFFFFF | #19281F | surface |
| surfaceVariant / `--soft` | #E8EFE7 | #26392D | surfaceContainer |
| onSurface / `--ink` | #182B20 | #F0F5ED | onSurface, teks utama |
| onSurfaceVariant / `--muted` | #526457 | #B2C4B6 | teks sekunder yang tetap terbaca |
| outline / `--line` | #738477 | #7F9484 | input, kontrol, divider kuat |
| subtleOutline / `--divider` | #D9E3D8 | #344C3C | pembatas dekoratif, bukan batas kontrol wajib |
| primary / `--primary` | #195B3D | #B7DEB8 | primary, tombol utama |
| onPrimary / `--on-primary` | #FFFFFF | #122C1C | teks tombol utama |
| primaryContainer / `--primary-soft` | #E0EFDF | #274C35 | pilihan aktif, konten pendukung |
| onPrimaryContainer / `--on-primary-soft` | #1B4E32 | #D5EDD3 | teks pilihan aktif |
| accent / `--accent` | #D2EF69 | #D2EF69 | progres, bidang ajakan ringkas |
| onAccent / `--on-accent` | #24390E | #24390E | semua teks di atas lime |
| error / `--error` | #A82832 | #FFB4BA | validasi, hapus |
| errorContainer / `--error-soft` | #FDECEE | #4A232B | bidang pesan gagal |
| warning / `--warning` | #735117 | #EDD28E | perhatian nonfatal |
| warningContainer / `--warning-soft` | #FAF0D7 | #3B321C | peringatan/draf editorial |

Bidang ringkasan utama menggunakan gradasi `#123F30` → `#226948` dengan teks `#FFFFFF` pada kedua tema untuk kesinambungan merek. Teks sekundernya `#DBEBDC`. Fokus memakai outline primary 3 px pada web dan indikasi Material pada Android. Chart utama memakai primary; track/baseline memakai divider. Destructive button terang memakai error + putih; gelap memakai error + `#380B12`.

Warna metode tidak menandakan standar internasional warna tong. Reduce/Reycle memakai keluarga hijau, Reuse menggunakan warning container, Trash memakai soft; selalu tampilkan kata metodenya. Kategori dikenali dari label dan ikon material, bukan hafalan tujuh warna.

### Kontras

Target teks normal minimal 4,5:1, teks besar minimal 3:1, batas kontrol/fokus yang diperlukan minimal 3:1 terhadap latar bersebelahan. Uji pasangan actual kedua tema, termasuk state selected, error dan disabled yang masih memuat informasi. Jangan menurunkan opacity teks terkunci sampai tidak terbaca. Hasil pemeriksaan paket tercatat pada [QA.md](QA.md).

### Material v1.1: gradasi dan kaca

Revisi 23 September mengikuti permintaan pengguna: tampilan lebih berlapis dengan sentuhan glassmorphism/liquid glass ala iPhone. Identitas pinus–lime dan font tetap dipertahankan. Efek ini adalah interpretasi visual untuk WasteNot, bukan implementasi material native Apple.

| Material / token CSS | Terang | Gelap | Penggunaan |
|---|---|---|---|
| `--pine-start/end` | #123F30 → #226948 | Sama | Summary, teks putih; sudut 135° |
| `--lime-start/end` | #EDF8BE → #C9EB72 | Sama | Reduce, avatar, ikon aktif; teks #24390E |
| `--action-start/end` | #226948 → #164B35 | #D0EBC1 → #ADD8B1 | Tombol primary; onPrimary mengikuti tema |
| `--canvas-start/end` | #FCFDF9 → #DFEEDB | #17261D → #203D2B | Latar ponsel, sudut 155° |
| `--glass-tint` | Putih 92% | #19281F 94% | Tint dock menjaga keterbacaan |
| `--glass-edge` | Putih 95% | #D0EDD6 24% | Garis pantulan 1 px, dekoratif |
| `--glass-shadow` | #163D27 14% | Hitam 38% | Bayangan dock dan frame |

Dock memakai radius 26, blur backdrop 16 px dan saturasi 1,2; inset highlight 1 px. Margin kiri/kanan 7 px, bawah 9 px di dalam frame mockup. Masing-masing dari lima target tetap minimal 48 dp tinggi. Pilihan aktif memiliki bidang lime, ikon, dan label; bukan transparansi saja. Dock berada dalam aliran layout sehingga tidak menutupi konten terakhir. Di Android tambahkan safe-area/navigation-bar inset sesuai perangkat, jangan menyalin margin frame sebagai inset sistem.

Glass dibatasi pada navigasi; form, pesan error, dan dialog tetap opak. Gradien ringkasan memberi fokus angka, sementara highlight tipis memberi kedalaman tanpa animasi refraksi. Tidak ada blur layar penuh, efek mengikuti pointer, atau gerak berulang. Pada browser tanpa backdrop-filter, dock memakai surface opak. Preferensi reduced-transparency juga mematikan blur. Tema gelap memakai tint gelap yang diuji terpisah.

Untuk Compose, terjemahkan gradasi menjadi brush dan bentuk/elevasi menjadi styling theme. Efek blur belakang adalah enhancement opsional: baseline Android menggunakan surface opak, border highlight, dan shadow yang sama. Pilih implementasi backdrop setelah profiling di perangkat minimum; blur pada isi komponen sendiri bukan pengganti blur konten di belakangnya. Tidak diperlukan dependency glass untuk memulai implementasi. Uji scrolling, konsumsi frame, fontScale, dan kontras sebelum mengaktifkannya pada produksi.

## 3. Tipografi dan font

Keluarga tunggal **Plus Jakarta Sans**, upright variable weight 200–800. Mockup memakai 400, 500, 600, 700, 800. Font berasal dari [repositori Google Fonts](https://github.com/google/fonts/tree/main/ofl/plusjakartasans), disertakan lokal bersama lisensi SIL Open Font License di `assets/fonts/`. Tidak ada request Google Fonts ketika HTML dibuka.

| Role | Ukuran / line height Android | Weight | Penggunaan |
|---|---|---|---|
| displayLarge | 48/56 sp | 800 | Angka utama dashboard/detail; bukan paragraf |
| displayMedium | 36/44 sp | 800 | Auth heading atau ringkasan kecil |
| displaySmall | 32/40 sp | 700 | Judul besar |
| headlineLarge | 28/36 sp | 700 | Judul halaman |
| headlineMedium | 24/32 sp | 700 | Judul sublayar |
| headlineSmall | 20/28 sp | 700 | Judul section penting |
| titleLarge | 18/26 sp | 700 | Header daftar/komponen |
| titleMedium | 16/24 sp | 600 | Judul baris/card |
| titleSmall | 14/20 sp | 600 | Label kelompok |
| bodyLarge | 16/24 sp | 400 | Isi, input, panduan |
| bodyMedium | 14/22 sp | 400 | Bantuan singkat, metadata utama |
| bodySmall | 12/18 sp | 400 | Metadata sekunder, bukan isi utama |
| labelLarge | 14/20 sp | 700 | Tombol |
| labelMedium | 12/18 sp | 600 | Chip dan navigasi |
| labelSmall | 11/16 sp | 600 | Status bar/ID dokumentasi saja |

Tracking teks normal 0; angka besar dapat memakai -1 sp; heading maksimal -0,5 sp. Gunakan tabular numerals untuk nilai yang berubah, unit tetap menempel secara visual. Weight tidak diubah dengan sintetis italic. Semua teks produksi masuk `strings.xml`; judul dokumen web boleh lebih besar dari skala aplikasi.

HTML memakai px untuk kanvas desain; Android menggunakan dp untuk jarak dan sp untuk teks. Jangan membuat ukuran sp mengikuti lebar perangkat secara otomatis. Pada font scale 200%, layout mengalir ulang dan tinggi baris/form bertambah.

### Font di Compose

Sumber variable font disertakan untuk web dan arsip. Untuk pemetaan paling sederhana di Android, ekspor instance statis dari font yang sama pada weight 400/500/600/700/800 dengan tool font yang mendukung OFL, atau gunakan dukungan variable font yang kompatibel dengan min SDK/toolchain tim. Verifikasi hasil pada perangkat minimum, jangan mengandalkan sintesis bold.

Nama resource yang disarankan: `plus_jakarta_sans_regular`, `medium`, `semibold`, `bold`, `extrabold`. Deklarasikan satu `FontFamily` dengan weight yang cocok. Buat semua 15 role Typography secara eksplisit agar tidak ada role yang diam-diam kembali ke font default. Sertakan lisensi saat mendistribusikan font.

## 4. Spacing, ukuran, bentuk, dan elevasi

| Token | dp / web px | Pemakaian |
|---|---|---|
| space1 | 4 | Gap ikon-label rapat |
| space2 | 8 | Metadata dan isi yang satu kelompok |
| space3 | 12 | Gap kontrol/baris compact |
| space4 | 16 | Isi card, antarfield |
| space5 | 20 | Padding halaman compact |
| space6 | 24 | Antarsection |
| space8 | 32 | Pembatas kelompok besar |
| space12 | 48 | Ruang auth/empty state |

Radius: 8 untuk badge status; 12 untuk button/input; 16 untuk card kecil; 24 untuk summary utama dan modal sheet atas. Pill hanya untuk chip yang memang dapat dipilih, bukan semua card. Avatar inisial boleh bulat.

Input minimal 56 dp; tombol utama minimal 52 dp; semua touch target minimal 48×48 dp. Ikon 24 dp, ikon kategori 24 dalam bidang 44–48 dp. NavigationBar sekitar 80 dp sebelum sistem inset, label 12 sp. App bar minimum 64 dp. Form bebas bertambah tinggi pada teks besar.

Elevasi 0 untuk daftar dan card dalam halaman, 1–2 dp untuk bar/FAB jika perlu membedakan layer, 6 dp untuk sheet/dialog. Bayangan frame ponsel di HTML hanya mengelompokkan pratinjau, tidak diterjemahkan ke UI Android. Blur hanya untuk dock navigasi dan contoh material yang sama di katalog.

## 5. Layout dan hierarki layar

Beranda: sapaan kecil → satu bidang angka dominan → tombol catat → informasi Reduce/rate/progres → aktivitas terbaru. Riwayat: filter dan daftar menjadi isi utama. Statistik: periode → total → seri harian → breakdown. Form: label dan kontrol dalam urutan baca, tidak memakai kartu per field.

Keputusan satu fokus per layar: S02/S03 form akses, S04 ringkasan hari ini, S05/S09 editor, S06 konfirmasi, S07 daftar aktivitas, S08 nilai catatan, S11 pencarian, S12 langkah penanganan, S13 total periode, S14 level, S15 syarat achievement, S16 pilihan tema, S17 opt-in, S18 pemilihan foto, S19 ketersediaan lokasi, S20 pesan state.

| Lebar tersedia | Pola Android |
|---|---|
| <600 dp | Satu kolom, NavigationBar, margin 20 dp |
| 600–839 dp | NavigationRail, form maksimal 560 dp, daftar lebih lapang |
| ≥840 dp | List-detail riwayat/panduan bila diimplementasikan; konten tidak meregang tanpa batas |

Breakpoint adalah keputusan layout proyek. Window resize, landscape, split-screen, keyboard, dan foldable mengikuti lebar tersedia, bukan deteksi nama perangkat. `Scaffold` padding/safeDrawing/IME inset ditangani sekali. Tombol simpan tidak menutup field terakhir.

Web dokumentasi: sidebar pada desktop; navigasi wrap di atas pada mobile; galeri 1–3 kolom dengan frame maksimum 390 px. Kanvas frame bisa discroll agar seluruh form dapat diperiksa. Kanvas galeri statis memiliki penanda dan tombol membuka versi interaktif. Jangan mengira tinggi frame preview adalah tinggi wajib layar Android.

## 6. Spesifikasi komponen

Inventaris C01–C34 lengkap ada di [SCREEN_SPEC](SCREEN_SPEC.md). Ketentuan visual lintas komponen:

| Kelompok | Default | Selected / focus | Disabled / error |
|---|---|---|---|
| Primary button | primary/onPrimary, radius 12, weight 700 | Umpan balik tekan, focus ring terlihat | soft/muted, label tetap terbaca; loading memberi teks |
| Secondary button | surface/ink, outline 1 | primary-soft + outline primary | Tidak menerima aksi ketika disabled |
| Text action | primary, tanpa bidang besar | Underline/focus yang jelas | Destructive memakai error |
| Field | surface, label di atas, outline 1 | Outline primary 2, caret kontras | Error outline + pesan teks; bukan warna saja |
| Method selector | Grid 2×2 / list saat ruang sempit | Primary-soft, radio/check, label | Helper sesuai metode; pilihan tunggal |
| Chip | soft, label 12–14 | primary/onPrimary atau primary-soft | Tombol hapus punya label aksesibel |
| Waste row | Ikon material + kategori/metode + nilai | Seluruh baris target sentuh | Tidak meredupkan angka penting |
| Progress | Track soft, fill primary/lime sesuai latar | Label nilai/current-target | Angka 0 bukan “tidak tersedia” |
| Badge | Radius 8, padding 4/8 | Status selalu tertulis | Locked achievement tetap 4,5:1 |
| Dialog | Surface, padding 24, heading 20 | Fokus berada di dialog | Aksi destruktif dan cancel terpisah |
| Empty/error | Ikon fungsional, judul, sebab, satu aksi | Fokus ke pemulihan | Tidak menampilkan statistik palsu |

Field dalam HTML adalah native input/select/date/time, bukan replika picker kompleks. Dalam Compose gunakan OutlinedTextField, SingleChoiceSegmentedButtonRow, RadioButton, FilterChip, DatePicker/TimePicker, ModalBottomSheet dan AlertDialog sesuai fungsi. Design system tidak mengharuskan satu wrapper Kotlin untuk setiap contoh.

## 7. Ikon, imagery, dan visualisasi

Ikon navigasi: rumah untuk Beranda, daftar/jam untuk Riwayat, buku untuk Panduan, batang untuk Statistik, siluet sederhana untuk Profil. Ikon kategori: daun (Organik), botol (Plastik), lembar (Kertas), wadah (Kaca), kaleng (Logam), perangkat (Elektronik), tiga titik (Lainnya). Label kategori tetap ditampilkan; ikon tidak menggantikan informasi.

Web memakai SVG lokal inline dengan viewBox 24×24 dan stroke 2. Android dapat memakai VectorDrawable dari path yang sama atau Material Symbols yang setara. `contentDescription=null` untuk ikon dekoratif di samping teks yang sudah menyebut maknanya; ikon aksi tunggal harus memiliki deskripsi. Hindari emoji karena bentuk dan baseline berbeda antarplatform.

Tidak ada foto stok pada baseline. Foto pengguna hanya muncul di panel lampiran setelah dipilih. Peta tanpa data menampilkan empty state jujur, bukan pin fiktif. Grafik menunjukkan aktivitas/berat tercatat, bukan pengurangan karbon. Label unit dan alternatif teks selalu tersedia.

## 8. State, microcopy, dan motion

| Situasi | Teks contoh | Aksi |
|---|---|---|
| Belum mencatat | “Belum ada catatan hari ini.” | Catat aktivitas |
| Reduce | “Dicatat terpisah dari sampah yang ditangani.” | Tetap isi form |
| Submit | “Menyimpan catatan…” | Tombol disabled |
| Sukses | “Catatan tersimpan.” | Lihat catatan |
| Gagal jaringan | “Belum tersimpan. Isianmu tetap tersedia.” | Coba lagi |
| Filter kosong | “Tidak ada catatan yang cocok.” | Reset filter |
| Hapus | “Statistik dan XP akan dihitung ulang.” | Hapus catatan / Batal |
| Sesi berakhir | “Masuk lagi untuk membuka catatanmu.” | Masuk |

Gunakan “sampah ditangani”, “benda dihindari”, dan “recycling rate berdasarkan aktivitas” sesuai PRD. Jangan menyebut “dampak terverifikasi”, “CO2 dihemat”, atau memberi nada menyalahkan untuk Trash. Label contoh data terlihat dalam dokumentasi dan setiap mockup.

Motion: state hover/press 120 ms, perubahan konten sederhana maksimum 180 ms; tidak ada parallax, loop konfeti, floating art, atau animasi angka terus menerus. Loading tetap punya teks. Hormati reduced motion di web dan animation scale sistem Android. Keberhasilan tidak bergantung pada animasi yang harus dilihat.

## 9. Aksesibilitas dan resilience

- Target sentuh 48 dp, urutan fokus logis, fokus terlihat, seluruh tombol punya nama.
- Dialog bisa dibatalkan, Escape pada web setara Back, fokus kembali ke pemicu.
- Label field persisten; placeholder bukan pengganti label. Error field terhubung dengan field.
- Input besar dan font scale 200% tidak memotong label. Teks panjang membungkus, bukan memaksa horizontal scroll.
- Chart memakai deskripsi, tabel/list alternatif, serta unit; tidak bergantung warna saja.
- State loading, empty, error, success tersedia di layar data. Gagal refresh mempertahankan data lama dengan penjelasan.
- Contrast kedua tema diperiksa. Theme gelap tidak sekadar membalik warna.
- Simulasi web tidak menyimpan password, tidak menghubungi backend, tidak mengaktifkan lokasi/notifikasi, dan tidak mengunggah foto.

## 10. Handoff Compose dan cakupan prototype

Pemetaan implementasi: `WasteNotTheme` memilih ColorScheme terang/gelap/sistem, memuat Typography Plus Jakarta Sans dan Shapes; `Dimens` mengelompokkan spacing. Theme menjadi sumber nilai styling, sedangkan model domain tetap di layer data/state. Dynamic color dimatikan secara default pada rancangan ini agar pasangan kontras dan identitas tidak berubah tanpa QA; dukungan wallpaper color dapat menjadi keputusan berikutnya.

HTML menyediakan seluruh S01–S20, katalog C01–C34, dialog D01–D04, switch tema, pencarian/filter, form validasi, simulasi tambah/ubah/hapus dan pembaruan ringkasan, pilihan state, serta preview foto lokal. Angka awal mengikuti PRD §9.4 dengan tanggal demo tetap 21 September 2026, meskipun revisi dokumen desain 23 September. Reset demo mengembalikan dataset acuan; reload juga mereset data simulasi.

Layar S10 adalah rancangan filter sheet; S18 panel lampiran; S20 galeri state, bukan tiga tab produk baru. Dalam galeri ditampilkan terpisah untuk handoff. Fitur autentikasi, conflict server, upload privat, permission Android, dan penjadwalan hanya disimulasikan. Penerimaan aplikasi Android tetap harus dibuktikan dengan implementasi dan pengujian di perangkat.

## 11. Checklist sebelum implementasi

- [ ] Semua warna komponen memakai token peran, bukan hex tersebar.
- [ ] Seluruh role Typography memakai keluarga dan weight yang benar.
- [ ] Field, bottom bar, FAB, dan dialog lolos font scale/inset checks.
- [ ] Warna metode dibantu label; Trash tidak ditampilkan sebagai kegagalan pengguna.
- [ ] Semua screen ID dan komponen yang benar-benar digunakan mempunyai padanan Compose.
- [ ] Dataset contoh tidak masuk build produksi sebagai hasil pengguna nyata.
- [ ] Konten panduan dan lokasi diverifikasi sebelum dipublikasikan.
- [ ] QA Android dicatat terpisah dari QA HTML dokumentasi.
