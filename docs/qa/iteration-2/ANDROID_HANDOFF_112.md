# Audit penerimaan Android #112

10 November 2026. Baseline `b772040`, hasil merge PR #113 ke `main`. Inventaris sumber mencakup **20 layar wajib dan 4 dialog**. Tabel menautkan bukti pada konfigurasi dan revisi masing-masing. Status parsial berarti masih ada bagian skenario yang perlu diuji. S20 mendeskripsikan state lintas layar.

## Layar dan navigasi

| Layar | Bukti yang tersedia | Review yang masih dibutuhkan |
| --- | --- | --- |
| S01–S03 | [Startup](STARTUP.md), [Masuk/Daftar](NAV_AUTH.md), [footer daftar](SECONDARY_SCREEN_REVIEW.md) | Pemulihan sesi dari refresh ditolak pada root. Urutan fokus lisan saat validasi dan IME aktif. |
| S04–S06, S08–S09 | [Bagian bawah konten](SCROLLED_CONTENT.md), [editor](EDITOR.md), [simpan](SAVE.md), [detail/Back](DETAIL.md), [regresi backend](AI_MAIN_REGRESSION.md) | Ulangi state write yang belum pasti dan konflik pada stack produksi. |
| S07, S10 | [Filter, halaman berikut, Back dan refresh](HISTORY_API.md) | Ukur frame dan memori dengan 1.000 catatan di perangkat. |
| S11–S12 | [Daftar kosong dari backend dan detail dengan fixture](GUIDE_API.md), [pencarian dan prefill](GUIDE.md) | Detail artikel PUBLISHED, sumber dan prefill dari katalog produksi. |
| S13–S14, S16 | [Statistik dan Profil dengan dataset PRD](PRD_DATASET_LIVE.md), [tema](THEME_DATASTORE.md), [streak melewati tengah malam](ACCOUNT_DAY_ROLLOVER.md) | Bandingkan S14 sebelum/sesudah perubahan callout. Verifikasi alternatif chart dan status streak dengan TalkBack. |
| S21–S25 | [Dua tema hingga akhir scroll](SCAN_THEMES.md), [kamera aktif](CAMERA_LIFECYCLE.md), [saran dan fallback](SCAN_REVIEW_FIXES.md), [kamera ke simpan nyata](AI_MAIN_REGRESSION.md) | Uji kamera fisik, kehilangan jaringan, cancel saat request aktif, dan capability off dari root. |
| D01–D04 | [Delete](DETAIL.md), [keluar dan buang draf](EXIT_DIALOGS.md), [konflik](CONFLICT.md), [pemulihan write](WRITE_RECOVERY.md) | Uji fokus TalkBack kembali ke pemicu setelah Back/batal, serta konflik nyata yang mempertahankan draf. |
| Dock, header, dan sheet | [Navigasi](SCREENS_NAVIGATION.md), [transisi](NAVIGATION_MOTION.md), [fokus keyboard](KEYBOARD_FOCUS.md) | Uji fokus lisan pada lima tab dan modal, rotasi pendek, serta CTA dengan IME terbuka. |

## Core

| ID | Status | Bukti dan batas |
| --- | --- | --- |
| AT-01 | Terverifikasi | [Isolasi akun](ACCOUNT_ISOLATION.md) mencakup daftar dan login ulang dari MainActivity dengan akun nyata. |
| AT-02 | Parsial | Validasi form dan kegagalan kredensial tersedia di `SessionRootTest`. Ulangi email duplikat dan kata sandi salah pada endpoint nyata. |
| AT-03 | Parsial | [Perjalanan AI](AI_MAIN_REGRESSION.md) menyimpan 250 g, satu log/10 XP, lalu membuka detail. Ulangi pembukaan detail setelah restart proses. |
| AT-04 | Parsial | [Recovery](WRITE_RECOVERY.md) mencakup hasil commit yang belum diketahui dan replay di UI terkontrol. [Transport](BACKEND_RECORDS.md) menguji replay tanpa duplikat. Gabungkan dengan putus koneksi aktual di alur produksi. |
| AT-05–06 | Parsial | `QuantityTest`, `EditorDraftTest` dan `QuantityRequestFieldsTest` menguji validasi model, form dan serialisasi. Audit penolakan HTTP untuk setiap input batas. |
| AT-07 | Terverifikasi | [Dataset PRD](PRD_DATASET_LIVE.md) mencatat Reduce 2 terpisah dari total penanganan. |
| AT-08–09 | Parsial | [Ringkasan backend](BACKEND_SUMMARIES.md) dan [fixture Statistik](STATISTICS.md) mencakup agregat serta unit terpisah. Ulangi dataset Reduce saja dan campuran unit lewat UI dengan akun nyata. |
| AT-10 | Terverifikasi | [PR #113](PRD_DATASET_LIVE.md) membuktikan 650 g, 1.400 g, 50%, 50 XP, streak 2 pada dua tema. |
| AT-11–12 | Parsial | [Riwayat API](HISTORY_API.md) membuktikan filter server, cursor dan Back dengan 22 catatan. `HistoryViewModelTest` menguji kombinasi filter dan respons terlambat. Jalankan skenario kombinasi dan race dari UI. |
| AT-13–14 | Parsial | [Perjalanan AI](AI_MAIN_REGRESSION.md) membuktikan edit tanpa XP ganda serta hapus yang mengembalikan agregat/progres ke nol. Ulangi delta tepat 250→300 g dan streak multi-hari sesudah penghapusan. |
| AT-15 | Parsial | [Konflik](CONFLICT.md) dan [transport catatan](BACKEND_RECORDS.md) mencakup 409 serta preservasi draf secara terpisah. Uji edit dua klien nyata sampai D04. |
| AT-16–17 | Terverifikasi | [Isolasi akun](ACCOUNT_ISOLATION.md) mencakup enam akses silang 404, dua sesi dan draf/stack per akun pada MainActivity. |
| AT-18–19 | Parsial | `SessionRepositoryTest` mencakup refresh terserialisasi, refresh ditolak dan 401 ulang. `SessionRootTest` mencakup navigasi ke root auth dengan fixture. Uji gabungan pada runtime dengan sesi expiry dan revocation. |
| AT-20 | Parsial | [Panduan](GUIDE.md) mencakup pencarian dan clear query. [Backend publik](GUIDE_API.md) membuktikan katalog kosong saat checkpoint. Ulangi kapitalisasi dengan artikel PUBLISHED. |
| AT-21 | Terverifikasi | [Dataset PRD](PRD_DATASET_LIVE.md) membuktikan periode 7/30, rentang, agregat dan chart. |
| AT-22 | Terverifikasi dengan jam terkontrol | [Pergantian hari](ACCOUNT_DAY_ROLLOVER.md) membuktikan refresh tanggal akun, periode, draf dan status streak pada respons terkontrol. |
| AT-23 | Parsial | [Pemulihan write](WRITE_RECOVERY.md) membuktikan PID berubah dan draf/UUID tetap tersedia pada fixture. [Isolasi](ACCOUNT_ISOLATION.md) membuktikan kepemilikan draf dengan akun nyata. Tambahkan pemeriksaan password dan process death dalam alur remote yang sama. |
| AT-24–25 | Parsial | [Footer/tema](SECONDARY_SCREEN_REVIEW.md), [fokus keyboard](KEYBOARD_FOCUS.md), dan [editor](EDITOR.md) memberi bukti terpisah. Jalankan TalkBack dengan verifikasi ucapan serta kombinasi landscape/IME/font 200%. |
| AT-26 | Parsial | [Riwayat API](HISTORY_API.md) membuktikan 1.000 catatan/50 halaman dalam unit test dan 22 catatan di MainActivity. Jank dan memori 1.000 catatan belum diukur. |

## Scan AI

| ID | Status | Bukti dan batas |
| --- | --- | --- |
| AT-AI-01 | Parsial | [Fokus keyboard](KEYBOARD_FOCUS.md) membuktikan fokus kembali ke pemicu. Uji fokus lisan dari lima tab. |
| AT-AI-02 | Parsial | [Lifecycle kamera](CAMERA_LIFECYCLE.md) menguji penolakan izin dan pemulihan preview. Uji permanent denial dan kamera tidak tersedia pada perangkat fisik. |
| AT-AI-03–04 | Parsial | [Lifecycle kamera](CAMERA_LIFECYCLE.md), [transport](AI_TRANSPORT.md) dan [regresi nyata](AI_MAIN_REGRESSION.md) mencakup capture, upload setelah tinjau, lifecycle, dan recreate. Uji retake/cancel saat operasi aktif dan process death dengan foto hilang. |
| AT-AI-05–06 | Parsial | [Urutan kandidat](SCAN_CHOICE_ORDER.md) membuktikan pilihan eksplisit. [Transport](AI_TRANSPORT.md) mempertahankan band server. Verifikasi batas skor/unknown dengan respons layanan yang terkontrol. |
| AT-AI-07 | Terverifikasi | [Regresi nyata](AI_MAIN_REGRESSION.md) meminta konfirmasi material untuk `bottle` meskipun skornya tinggi. |
| AT-AI-08 | Parsial | `ScanViewModelTest` membuang hasil terlambat saat pilihan dikoreksi. Uji perubahan kondisi ketika request sedang aktif dari UI. |
| AT-AI-09–10 | Parsial | [Transport](AI_TRANSPORT.md) memvalidasi identitas rekomendasi dan membedakan special handling. [Policy-08](POLICY08_ANDROID.md) membuktikan tampilan langkah/sumber terkontrol. Periksa kasus layanan nyata dengan kebijakan backend yang berlaku. |
| AT-AI-11 | Parsial | [Transport](AI_TRANSPORT.md) mensyaratkan pelepasan provenance untuk Reduce. Jalankan skenario koreksi ke Reduce dari UI nyata. |
| AT-AI-12 | Parsial | `ScanViewModelTest` mencakup timeout, Retry-After dan jalur manual eksplisit. Uji putus jaringan pada perangkat/staging. |
| AT-AI-13 | Parsial | [Regresi nyata](AI_MAIN_REGRESSION.md) membuktikan 250 g, satu log/10 XP dan provenance. [Transport](AI_TRANSPORT.md) mencakup replay setelah acknowledgment hilang. Uji dua aksi simpan dalam alur UI yang sama. |
| AT-AI-14 | Parsial | [Transport](AI_TRANSPORT.md) memvalidasi foreign/expired dan [review scan](SCAN_REVIEW_FIXES.md) menguji expiry/manual. Uji 404/410/409 dari layanan dengan dua akun dan referensi yang sudah dipakai. |
| AT-AI-15–16 | Parsial | `ScanViewModelTest`, `ScanPhotoCacheTest` dan [transport](AI_TRANSPORT.md) mencakup koordinat, expiry dan cleanup lokal. Uji file rusak/besar, orientasi kamera fisik, serta deadline retensi setelah crash. |
| AT-AI-17 | Parsial | [Dua tema](SCAN_THEMES.md) dan [daftar objek](SCAN_CHOICE_ORDER.md) membuktikan pilihan melalui daftar. Uji TalkBack lisan, 200%, keyboard dan landscape pendek bersama. |
| AT-AI-18 | Parsial | [Fallback Policy-08](POLICY08_ANDROID.md) membuktikan input manual tersedia saat saran gagal. Uji `ai_scan=false` pada metadata backend dan perjalanan auth/Riwayat. |

## Penyimpanan aktivitas

`RemoteActivityStore` membaca aktivitas melalui `CoreRepository`. Tema disimpan oleh DataStore, draf memakai SharedPreferences per akun, dan foto sementara memakai file lokal. Riwayat setelah cold start offline memerlukan keputusan terpisah tentang Room per pemilik, sumber pembacaan UI, antrean sinkronisasi, retry, resolusi versi, logout dan penghapusan data.
