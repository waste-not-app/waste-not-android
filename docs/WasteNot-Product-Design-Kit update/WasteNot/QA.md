# WasteNot: Pemeriksaan Design Kit

Tanggal pemeriksaan terbaru: 13 November 2026 (penyempurnaan mockup v1.4 iOS Inset-Grouped + Liquid Glass). Bukti material v1.1–v1.3 di bawah tetap dicatat sebagai riwayat. Cakupan: HTML/CSS/JavaScript, dokumen, font, dan simulasi interaksi. **Bukan** hasil pengujian aplikasi Android atau backend.


## Hasil v1.4 (iOS Inset-Grouped + Liquid Glass)

- PASS `verify.cjs`: seluruh 22 pemeriksaan lulus (25 layar/panel, 39 komponen, 360/390/768/1024/1512 px pada terang & gelap, stress teks 200%, 4 dialog, 8×5 state, form/filter/panduan/statistik/reminder/foto/lokasi, 0 JS error).
- PASS `verify-ai.cjs`: seluruh flow AI bottom sheet (4 lebar × 2 tema, Escape/focus restore, multi/low/none/special/offline/LLM fallback, kamera virtual, 200% text stress, 0 JS error) lulus.
- Kontras token baru v1.4 dihitung dengan formula WCAG AA: 10/10 pasangan terang/gelap ≥ 5,96:1 (target ≥ 4,5:1).
## Hasil AI v1.2

- PASS `verify.cjs`: 25 layar/panel, 39 komponen, lima viewport pada dua tema; regresi create/edit/delete, state, unit, navigasi dan font 200% lulus, tanpa JavaScript error.
- PASS `verify-ai.cjs`: sheet pada 360/390/768/1512 px × dua tema; Escape menutup dan mengembalikan fokus ke action utama.
- PASS: kategori wajib dikonfirmasi, metode dapat dikoreksi, jumlah tetap kosong; scan tidak menambah record/XP, satu submit valid menambah satu log.
- PASS: multi-object, skor rendah, unknown, special handling, jaringan gagal dan LLM gagal mempunyai manual fallback. Special handling tidak menyediakan tombol gunakan saran.
- PASS: capture dan retake menggunakan kamera virtual Chromium; capture/close menghentikan track. Penolakan izin diuji dengan fake getUserMedia rejection. Kamera fisik/Android belum diuji.
- PASS: hasil async yang dibatalkan diabaikan; manual dapat dicapai pada teks sheet 200%; tidak ada JavaScript error.
- Review visual: Beranda dengan action dock, sheet kamera dan hasil scan, serta preview S21–S25 diperiksa. Foto tidak diunggah; bbox dan saran berlabel ilustrasi/simulasi.
- Dokumen PRD, DESIGN, SCREEN_SPEC, API, ARCHITECTURE, IMPLEMENTATION_PLAN, README diperbarui; AI_SPEC dan salinan plan sumber ditambahkan. Kontrak API/infrastructure masih rancangan.

Belum diuji: YOLO nyata, model/dataset/accuracy/calibration, LLM provider/safety aktual, endpoint/backend/retensi file, latency/biaya, kamera Android dan izin perangkat fisik. Tidak ada klaim hasil AI berdasarkan simulasi browser.

## Hasil yang diperiksa

| Pemeriksaan | Hasil / bukti |
|---|---|
| Inventaris | PASS: 25 elemen galeri S01–S25, 39 komponen C01–C39, 4 dialog D01–D04 |
| File offline | PASS: dibuka melalui file lokal dengan Chromium; font lokal dimuat; tidak memerlukan server data |
| Desktop/mobile | PASS: semua 25 layar pada lebar 360, 390, 768, 1024, 1512 px; tidak ada horizontal overflow |
| Tema | PASS: matriks ukuran diulang untuk terang dan gelap; kontrol tema dan alur pencatatan juga dicoba |
| State | PASS: 8 layar data × loading/empty/error/offline/content; ditambah 6 contoh state pada S20 |
| Angka contoh | PASS: 1.400 g, 2 benda dihindari, 50% recycling rate, streak 2; denominator kosong menghasilkan null |
| Validasi unit | PASS: g, kg dengan koma/titik, integer benda, batas nilai, NaN/Infinity/eksponen ditolak |
| Konversi unit | PASS: 250 g → 0,25 kg → 250 g; pindah ke benda mengosongkan nilai agar tidak membuat konversi palsu |
| Kontras | PASS: 28 pasangan token teks minimal 4,5:1, nilai terendah terukur 5,40:1; 4 pasangan outline/background minimal 3:1 |
| Font | PASS: Plus Jakarta Sans variable memuat axis wght 200–800; lisensi OFL disertakan |
| Teks besar | PASS: stress font 200% pada Beranda, form, riwayat, dan statistik di viewport 390 px tanpa horizontal overflow |
| Visual | PASS: screenshot semua 20 layar ditinjau; form/konten panjang dapat discroll; label dan CTA tetap berada dalam frame |
| Runtime | PASS: tidak ada JavaScript page error pada pemeriksaan browser |
| Dokumen | PASS: source kebutuhan dan keputusan baru dibedakan; referensi styling Titip.in diganti arah WasteNot |

Stress CSS 200% adalah pemeriksaan web, bukan pengganti uji Android fontScale/TalkBack. Kontras yang diperiksa adalah pasangan warna yang ditetapkan sistem; laporan ini bukan sertifikasi WCAG seluruh produk.

## Revisi material v1.1

- Gradasi, canvas, dan komposit tint glass: 1.212 sampel warna pada kedua tema, rasio teks minimum 5,24:1 (target 4,5:1). Sampel glass menguji latar hitam–putih pada opacity tint yang ditentukan; teks dock memakai onSurface. Ini pemeriksaan warna, bukan sertifikasi visual seluruh piksel.
- Blur dock aktif pada Chromium di kedua tema; kelima target navigasi minimal 48×48 px pada viewport 360 px.
- Reduced motion mempertahankan scroll tanpa animasi; forced colors mematikan backdrop blur. CSS juga menyediakan fallback surface opak ketika backdrop-filter tidak tersedia atau reduced-transparency aktif; dua kondisi terakhir diperiksa pada aturan CSS, bukan perangkat fisik.
- Matriks dan click-through di bawah diulang dengan styling v1.1. Screenshot 20 layar per tema dibuat untuk pemeriksaan visual; preview desktop diperbarui.
- Implementasi ini adalah interpretasi glass untuk web. Efek native Liquid Glass Apple dan performa backdrop Android tidak diklaim telah diimplementasikan atau diuji.

## Bukti click-through

| Elemen/alur | Tindakan dan hasil |
|---|---|
| Pilihan layar | S01–S25 masing-masing dibuka melalui selector dan tombol galeri; ID tujuan cocok |
| Filter galeri | Akses/tracking/insight/profile/optional/semua mengubah kumpulan preview |
| Theme selector | Terang/gelap memperbarui seluruh halaman dan prototype; sistem mengikuti preference browser |
| Form catatan kosong | Simpan menampilkan permintaan memilih metode |
| Berat invalid | Nilai 0 ditolak dengan pesan berat 1 g–100 kg |
| Create | 100 g Recycle membuat satu catatan baru, jumlah log dari 5 menjadi 6 |
| Detail | “Lihat catatan” membuka 100 g yang baru dibuat |
| Edit | Nilai diubah ke 300 g; jumlah log tetap 6, sehingga tidak ada XP create tambahan |
| Hapus | Batal mempertahankan log; konfirmasi mengembalikan jumlah log ke 5 |
| Metode Reduce | Disimpan pada kedua tema; ditampilkan terpisah dari berat ditangani |
| Filter riwayat | Plastik AND Recycle menghasilkan satu baris; reset kembali lima |
| Reset pilihan sheet | Mengosongkan kategori/metode/tanggal draft filter, tidak kembali ke nilai filter awal |
| Search panduan | Query tanpa hasil memunculkan empty state; hapus mengembalikan daftar |
| Detail panduan | Artikel terbuka; “Catat aktivitas ini” mengisi kategori Plastik dan metode Recycle |
| Statistik | Toggle 7/30 hari mengubah rentang; “Lihat angka harian” membuka data tekstual |
| Draf form | Back membuka konfirmasi; Escape mempertahankan teks; Buang perubahan menghapus draf |
| Selector saat dirty | Batal kembali menampilkan ID layar aktif, bukan tujuan yang dibatalkan |
| Dialog | Keempat dialog dibuka; keyboard berpindah ke aksi dialog; Escape menutup; konflik memuat versi simulasi terbaru |
| Reminder | Opt-in mengaktifkan isian waktu; 21:30 tersimpan sebagai preference simulasi; off menonaktifkan waktu |
| Foto | File PNG lokal menampilkan preview; Hapus menghilangkan preview; text/plain ditolak |
| Lokasi | Cari area menyatakan data belum ada; simulasi permission denied memberi fallback pencarian |
| State gallery | Loading/kosong/error/offline/sesi/filter masing-masing mengubah pesan dan aksi |
| Registrasi | Konfirmasi kata sandi berbeda ditolak; nilai cocok masuk ke akun simulasi |
| Logout/login | Logout mengosongkan data sesi demo; login membuka Beranda kosong |
| Bottom navigation | Kelima tab mengarahkan ke Beranda/Riwayat/Panduan/Statistik/Profil |
| Password component | Tombol mata mengganti masked/visible |
| Filter chip | Klik mengganti aria-pressed |
| Segment component | Pilihan 30 hari menjadi satu-satunya pilihan aktif |
| Filter sheet component | Membuka overlay; kategori Kaca menghasilkan satu baris setelah Apply |
| Snackbar component | Tombol menampilkan feedback berstatus live region |
| Switch component | Checkbox mengubah state dan feedback tanpa permission browser |
| Search component | Teks masukan muncul di feedback pencarian contoh |
| Native input/picker | Field teks, select, date, time, radio, checkbox memakai perilaku browser bawaan |
| Reset demo | Memulihkan lima record awal dan membersihkan draft/filter/preview |

Kontrol noninteraktif seperti badge, progress, ikon dekoratif, dan preview galeri tidak disajikan sebagai tombol palsu. Tautan dokumen/anchor diperiksa tujuannya pada paket lokal. Simulasi tidak melakukan authentication server, transaksi database, upload, geolocation, atau scheduling.

## Delivery gate Antislop

### Hard gate

- R-02 PASS: tidak ada em dash dalam konten HTML/DESIGN yang diserahkan.
- R-03 PASS: matriks 25 layar × 5 lebar × 2 tema tidak menghasilkan horizontal overflow.
- R-17 PASS: angka produk berasal dari dataset simulasi yang diberi label; jumlah layar/komponen dihitung dari DOM.
- R-18 PASS: tidak ada testimonial, foto orang, atau klaim pengguna fiktif.
- R-23 PASS: pengguna meminta penentuan arah desain; wordmark teks, avatar inisial demo, dan placeholder editorial ditandai.
- R-24 PASS: navigasi dokumentasi mengarah ke bagian/file yang tersedia; lima tab aplikasi memiliki tujuan nyata.
- R-25 PASS: pasangan teks token terukur ≥5,40:1; pasangan batas kontrol diuji ≥3:1.
- R-26 PASS: perilaku kontrol tercatat pada matriks click-through; kontrol disabled/preview memiliki peran jelas.
- R-27 PASS: state loading, empty, error, offline, content tersedia pada layar data; success dan session state juga ditampilkan.
- R-28 PASS: tidak ada FAQ pengisi ruang.
- R-32 PASS: native button/input/dialog dapat digunakan dengan keyboard; focus-visible didefinisikan; Escape membatalkan dialog.
- R-33 PASS: implementasi berada langsung pada HTML/CSS/JS; tidak ada helper patch UI yang disertakan.
- R-34 PASS: tema terang dan gelap diuji pada seluruh lebar serta interaksi pencatatan.
- R-35 PASS: Chromium menjalankan prototype, click-through dan screenshot tersedia selama QA; `verify.cjs` disertakan untuk mengulang pemeriksaan.
- R-36 PASS: tidak ada klaim performa produksi, keamanan tersertifikasi, atau hasil pengurangan lingkungan.
- R-37 PASS: DESIGN.md menetapkan arah terlebih dahulu; palet/font mandiri sesuai instruksi pengguna.
- R-38 PASS: data demo, panduan editorial, Maps tanpa dataset, dan opsi fitur dinyatakan secara eksplisit.

### Purpose gate

- R-01 PASS: pinus untuk aksi, lime untuk progres; alasan tercatat di DESIGN §1–2.
- R-04 PASS: simbol menunjukkan material/tindakan, dengan label; pemilihan dijelaskan di DESIGN §7.
- R-06 PASS: Plus Jakarta Sans dipilih untuk UI dan angka; satu keluarga dan skala role terdokumentasi.
- R-07 PASS: gradasi pinus/lime membedakan ringkasan dan progres; latar mint memberi dasar dock sesuai permintaan pengguna.
- R-08 PASS: panah hanya Back/chevron navigasi, bukan dekorasi setiap CTA.
- R-09 PASS: badge dipakai untuk metode/status; tanpa badge promosi.
- R-10 PASS: glassmorphism diminta pengguna, dibatasi pada dock dan sampel materialnya; form/dialog opak.
- R-12 PASS: bayangan membedakan dock/frame/overlay; highlight tipis pada summary dan CTA; daftar tetap tanpa bayangan.
- R-13 PASS: tidak memakai glow.
- R-14 PASS: layout aplikasi mengikuti form/list/statistik; grid komponen dan galeri dipakai untuk perbandingan katalog.
- R-19 PASS: transisi kontrol pendek, reduced motion tersedia, tanpa gerak dekoratif berulang.
- R-22 PASS: tidak memakai ilustrasi stok yang tidak terkait; ikon bersifat fungsional.

### Liveliness dan quality locks

- Dials PASS: ENERGY 2 / RHYTHM 2 / MOTION 1 tertulis sebelum mockup dibuat.
- Focal point PASS: angka, form, daftar, atau tindakan utama dibedakan per layar dalam DESIGN §5.
- Whitespace PASS: spacing 4–48 dan pembagian section/field diterapkan konsisten.
- Accent PASS: lime menandai pencegahan/progres; primary menangani aksi.
- Identity PASS: angka tebal, satuan, bidang pinus dan lime menjadi pola identitas yang berulang.
- Design read PASS: konteks pengguna, bahasa visual, dan dials tercatat di DESIGN §1.
- C-1 PASS: alasan warna, font, layout, spacing, ikon, bentuk tercatat.
- C-2 PASS: perilaku kontrol diuji dan keterbatasan simulasi dinyatakan.
- C-3 PASS: section berisi kebutuhan fondasi, komponen, layar, prototype, dan handoff.
- C-4 PASS: ukuran, tema, state, dan keyboard diperiksa; validasi Android tetap pekerjaan implementasi.
- C-5 PASS: tidak ada angka dampak atau pengguna yang disajikan sebagai fakta.
- R-05 PASS: halaman dokumentasi memiliki tipe konten berbeda; aplikasi mengikuti pekerjaan tiap layar.
- R-11 PASS: radius 8/12/16/24 berbeda sesuai fungsi; dock memakai radius 26; input tetap radius 12.
- R-15 PASS: CTA menyebut aksi seperti Simpan catatan, Terapkan filter, dan Hapus foto.
- R-16 PASS: tidak ada buzzword pemasaran yang menggantikan fungsi produk.
- R-20 PASS: desain disusun untuk catatan 3R, tidak menyalin UI marketplace sumber.
- R-21 PASS: tersedia pilihan terang/gelap/sistem.
- R-29 PASS: pinus dan lime sebagai warna identitas; warna error/warning dipakai secara semantik.
- R-30 PASS: tidak menyalin susunan produk populer atau contoh Titip.in.
- R-31 PASS: alasan keputusan besar tersedia dalam DESIGN §1.

## Belum dibuktikan oleh paket ini

Build Android, TalkBack pada perangkat, fontScale Android, API ownership, session security, latency backend, query performance, proses upload privat, WorkManager, Maps provider, dan kebenaran editorial panduan. Semua itu harus diuji saat implementasi, mengikuti IMPLEMENTATION_PLAN.md.

## Bukti mesin v1.2

Laporan yang disertakan: [regresi core](assets/qa-core-v1.2.json), [AI browser simulation](assets/qa-ai-v1.2.json), dan [preview alur AI](assets/preview-ai.png). `verify.cjs` dan `verify-ai.cjs` dapat dijalankan ulang sesuai README. Pemeriksaan struktur menemukan 60 tautan lokal valid; salinan plan cocok byte-per-byte dengan berkas sumber.

## Verifikasi mockup v1.3

- PASS: 22 kelompok regresi core, 25 layar × 5 viewport × 2 tema; interaksi, font 200% dan JavaScript error checks.
- PASS: alur sheet pada empat viewport × dua tema, skenario AI dan manual, capture kamera virtual, permission denied, dan fallback pada font 200%.
- PASS: memilih contoh simulasi menghentikan kamera; stream dari izin yang datang terlambat langsung dihentikan; Escape menghentikan track sebelum dialog selesai ditutup.
- PASS: draf dirty tidak ditimpa scan baru. Koreksi mempertahankan objek, kategori, kondisi dan reuse intention; penggantian material mereset kondisi sebelumnya.
- Review visual: camera sheet dengan step indicator, footer manual, dan preview form kondisi memakai komponen yang sama dengan interaksi; teks dan target utama terbaca.
- C07, susunan katalog, DESIGN, SCREEN_SPEC dan README diselaraskan dengan aksi manual sekunder serta Catat/Scan utama. Kontrak domain/API v1.2 tidak berubah.

Bukti terbaru: [core v1.3](assets/qa-core-v1.3.json), [AI browser v1.3](assets/qa-ai-v1.3.json), [preview](assets/preview-ai.png). Laporan v1.2 disimpan sebagai riwayat. Kamera fisik, Android, YOLO dan LLM produksi belum diuji.
