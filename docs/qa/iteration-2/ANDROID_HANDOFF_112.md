# Audit penerimaan Android #112

10 November 2026. Baseline `b772040`, hasil merge PR #113 ke `main`. Inventaris sumber mencakup **20 layar wajib dan 4 dialog**. Tabel menautkan bukti pada konfigurasi dan revisi masing-masing. Status parsial berarti masih ada bagian skenario yang perlu diuji. S20 mendeskripsikan state lintas layar.

## Layar dan navigasi

| Layar | Bukti yang tersedia | Review yang masih dibutuhkan |
| --- | --- | --- |
| S01–S03 | [Startup](STARTUP.md), [Masuk/Daftar](NAV_AUTH.md), [footer daftar](SECONDARY_SCREEN_REVIEW.md), [refresh dan revocation pada root](#checkpoint-sesi-dan-konflik-versi), [TalkBack pada Masuk](#checkpoint-talkback-masuk) | Penerimaan ucapan aktual dan alur Daftar dengan TalkBack. |
| S04–S06, S08–S09 | [Bagian bawah konten](SCROLLED_CONTENT.md), [editor](EDITOR.md), [simpan](SAVE.md), [detail/Back](DETAIL.md), [konflik versi nyata](#checkpoint-sesi-dan-konflik-versi), [pemulihan koneksi di QA](#checkpoint-respons-simpan-terputus) | Ulangi pemulihan koneksi pada lingkungan HTTPS staging. |
| S07, S10 | [Filter, halaman berikut, Back dan refresh](HISTORY_API.md), [baseline native 1.000 catatan](#checkpoint-riwayat-1000-catatan) | Profil ulang di perangkat fisik pada build yang representatif untuk distribusi. |
| S11–S12 | [Katalog produksi kosong](GUIDE_API.md), [pencarian dan prefill](GUIDE.md), [artikel PUBLISHED pada backend QA lokal](#checkpoint-panduan-dan-scan-dinonaktifkan). Pemeriksaan ulang 10 November menemukan 0 artikel PUBLISHED di produksi. | Penerimaan konten saat artikel terbit tersedia di katalog produksi. |
| S13–S14, S16 | [Statistik dan Profil dengan dataset PRD](PRD_DATASET_LIVE.md), [tema](THEME_DATASTORE.md), [review callout S14](#checkpoint-s14) | Verifikasi alternatif chart dan status streak dengan TalkBack. |
| S21–S25 | [Dua tema hingga akhir scroll](SCAN_THEMES.md), [kamera aktif](CAMERA_LIFECYCLE.md), [saran dan fallback](SCAN_REVIEW_FIXES.md), [kamera ke simpan nyata](AI_MAIN_REGRESSION.md), [capability off](#checkpoint-panduan-dan-scan-dinonaktifkan) | Uji kehilangan jaringan dan cancel saat request aktif. Uji kamera fisik ditunda sesuai cakupan emulator. |
| D01–D04 | [Delete](DETAIL.md), [keluar dan buang draf](EXIT_DIALOGS.md), [pemulihan write](WRITE_RECOVERY.md), [D04 dengan backend](#checkpoint-sesi-dan-konflik-versi) | Uji fokus TalkBack kembali ke pemicu setelah Back/batal. |
| Dock, header, dan sheet | [Navigasi](SCREENS_NAVIGATION.md), [transisi](NAVIGATION_MOTION.md), [fokus keyboard](KEYBOARD_FOCUS.md) | Uji fokus lisan pada lima tab dan modal, rotasi pendek, serta CTA dengan IME terbuka. |

Regresi pada API 35 menjalankan 15 metode `ScreenKitReviewTest`, `SessionRootTest` dan dua kasus `ScanSheetTest`. Empat belas lulus, termasuk scroll konten, dock, pemulihan sesi terkontrol, expiry scan dan jalur manual. Satu tes gabungan editor/filter/dialog berhenti saat Android menolak injeksi sentuhan ke window. Metode yang sama lulus saat dijalankan sendiri pada API 37 dalam 60,15 detik.

Regresi lanjutan di API 37 lulus 18 tes. Ini mencakup pemulihan sesi dan submit IME, alternatif teks grafik dan unit jumlah, pilihan objek/material, expiry, serta langkah dan sumber saat saran AI belum tersedia. [Hasil native](handoff-112/followup-emulator.log)

## Checkpoint TalkBack Masuk

Di emulator Android 17/API 37, font 200%, 1080×2400 px dan 420 dpi, tes lama terkadang mulai mencari tombol saat fokus sudah pindah ke Gboard. Probe pada APK aplikasi awal juga lulus ketika IME belum terbuka. Saat Gboard terbuka, tombol dapat terlihat di viewport aplikasi ketika TalkBack sedang menelusuri window keyboard.

`TalkBackNavigationTest` sekarang membuka IME dan menunggu sampai terlihat, lalu mengembalikan fokus aksesibilitas ke kontrol Tampilkan kata sandi. TalkBack 17 dengan Enhanced keymap harus mencapai tombol Masuk yang dapat diaktifkan pada langkah maju berikutnya. Tes mengaktifkan tombol, memeriksa pesan validasi kata sandi di window aplikasi, lalu memakai langkah mundur untuk mencapai pesan di atas tombol dan langkah maju untuk kembali ke Masuk. Tiga pengulangan lulus dengan Gboard terbuka dan font 200%. Perubahan ada pada driver pengujian. Pengujian ini memeriksa layanan TalkBack, fokus dan aksi aksesibilitas. Verifikasi ucapan yang terdengar tetap terbuka. [Screenshot](handoff-112/talkback-masuk-font200.png), [hasil native](handoff-112/talkback-masuk-native.log), [pintasan TalkBack](https://support.google.com/accessibility/android/answer/6110948?hl=en)

APK debug SHA-256 `4adc2a89ddf20c48ee3293a153fed1e5e3d8c9b844f3b08e46d3f537ef7ff5b8`. APK penguji SHA-256 `fa5d6beb5d62802a43a9af537c25ba033983f3618f68647accfee598671995e8`. Pada emulator QA dengan TalkBack Enhanced aktif, font 200%, Gboard dan kedua APK terpasang, jalankan perintah berikut.

```sh
adb shell settings put secure show_ime_with_hard_keyboard 1
adb shell am instrument -w -e talkback true -e talkbackKeymap enhanced -e class com.example.wastenot.ui.main.TalkBackNavigationTest com.example.wastenot.test/com.example.wastenot.WasteNotTestRunner
```

## Checkpoint S14

Kartu streak mengikuti mockup S14 dengan ikon jam dekoratif, gradien Lime dan judul hari beruntun. Rekor terbaik serta dasar zona akun tetap tersedia. Nilai nol dan nilai yang belum tersedia dibedakan, termasuk ketika server hanya memberi satu dari dua nilai. Streak positif menampilkan status pencatatan hari ini dari server.

| Pembanding | Terang | Gelap |
| --- | --- | --- |
| Design kit | [Referensi](profile-reference-light.png) | [Referensi](profile-reference-dark.png) |
| Sebelum, UI terkontrol | [3/7 hari](backend-contract-preview/S14-streak-light.png) | [3/7 hari](backend-contract-preview/S14-streak-dark.png) |
| Sesudah, UI terkontrol | [3/7 hari](handoff-112/profile-light.png) | [3/7 hari](handoff-112/profile-dark.png) |
| Sesudah, landscape dan font 200% | [3/7 hari](handoff-112/profile-landscape-200-light.png) | [3/7 hari](handoff-112/profile-landscape-200-dark.png) |

Keadaan tambahan yang diperiksa meliputi [nol](handoff-112/profile-zero.png), [belum tersedia](handoff-112/profile-unknown.png), dan [streak kemarin melalui navigasi akun](handoff-112/profile-yesterday-app.png). `ProfileDesignTest` dan `AccountDayNavigationTest` lulus tujuh metode pada Pixel 8a emulator Android 15/API 35. Pengulangan landscape/font 200% meluluskan kasus Profil dan akses aksi pemulihan sesi pada jendela pendek. Gambar komponen diambil setelah menggulir ke streak. APK aplikasi memakai adapter remote. Respons streak dan jam di bukti baru berasal dari fixture pengujian.

APK staging `com.example.wastenot.staging` berhasil dipasang dan membuka Masuk pada API 35. Minimum SDK 30, target 36, SHA-256 `62e61d897d9373bb45e3d1e41a7c63c94f8c7f9de2dca5120affc14eea99f159`.

## Checkpoint Riwayat 1.000 catatan

`HistoryDesignTest#measuresNativeHistoryLoadingAndScrollingOneThousandRecords` lulus di emulator Android 17/API 37, 1080×2400 px, 420 dpi, font 100%, debug native dan renderer perangkat lunak. `HistoryScreen` dan `HistoryViewModel` menerima 50 halaman fixture berisi 20 catatan. Halaman kedua sengaja gagal sekali. UI tetap menampilkan 20 hasil awal dan Coba lagi memakai cursor yang sama. Semua halaman dimuat melalui aksi daftar, kemudian test menggulir daftar dengan gestur dan kembali ke bagian atas.

- [Tangkapan daftar](handoff-112/history-1000.png) dan [hasil pagination/PSS](handoff-112/result.txt). 51 permintaan mencakup 50 cursor unik. PSS proses aplikasi bertambah dari 97.313 KiB menjadi 107.362 KiB. Dataset fixture dan runner sudah berada dalam proses saat sampel awal.
- [Frame debug](handoff-112/gfxinfo.txt). 572 dari 632 frame (90,51%) tercatat jank dan p95 750 ms selama pagination, loncat indeks dan gestur. Waktu fase terukur 93,58 detik. Angka mencakup biaya driver UI dan renderer emulator saat host masih menjalankan pekerjaan build. Gunakan pengukuran perangkat fisik dengan build representatif untuk keputusan performa.
- APK debug SHA-256 `8aa0c7082b7ce81540aecbf9a3654e222826c31f53c41dda440566a4de785fcd`. APK penguji SHA-256 `e0260e26a1a3c7e135d20ade1199bb058e76aee150f1742c5f7ebd8326df9f8b`.

Perintah berikut mengulang uji dengan APK debug dan penguji yang sudah dipasang pada emulator QA.

```sh
adb shell am instrument -w -e historyBenchmark true -e class 'com.example.wastenot.ui.main.HistoryDesignTest#measuresNativeHistoryLoadingAndScrollingOneThousandRecords' com.example.wastenot.test/com.example.wastenot.WasteNotTestRunner
```

## Checkpoint sesi dan konflik versi

`RemoteSessionRecoveryTest` lulus dalam 23,64 detik di emulator Android 17/API 37. MainActivity, Keystore, gateway HTTPS dan database Go aktif `dc127354` dipakai pada satu akun probe. APK debug sama dengan benchmark Riwayat. SHA-256 APK penguji `0f56a6ea966aad2e2971c2050c980702c9b832f696a514f9ff627e55425b420e`.

1. Buat catatan 250 g. Saat editor menyimpan draf 300 g, penulisan kedua via API sudah mengubah versi ke 275 g. D04 memuat kedua versi. Memilih draf belum menulis ke server. Setelah konfirmasi Simpan, catatan yang sama menjadi 300 g dan progres tetap 10 XP. [D04](handoff-112/session-recovery/version-conflict.png)
2. Driver menaikkan generation hanya pada sesi probe. Backend menolak access token lama dengan 401. Aplikasi melakukan satu rotasi refresh dan request dashboard berhasil. Root mempertahankan identitas, catatan 300 g dan 10 XP. Expiry waktu JWT alami masih perlu diukur. [Beranda setelah refresh](handoff-112/session-recovery/root-after-refresh.png)
3. Driver mencabut sesi probe. Backend menolak refresh, generation dan jumlah token tidak bertambah. Aplikasi membersihkan Keystore dan menampilkan Masuk dengan pesan sesi. [Root setelah revocation](handoff-112/session-recovery/root-after-revocation.png)
4. Driver menghapus akun probe dengan guard email, nama dan UUID. User, sesi, refresh token, catatan, operasi AI dan metadata terkait semuanya nol setelah cleanup. [Log pengujian](handoff-112/session-recovery/native.log), [hasil database](handoff-112/session-recovery/result.json)

Driver opt-in memakai APK debug dan penguji yang sudah terpasang serta alias SSH operator `cp-bcc`.

```sh
python3 scripts/verify_live_session_recovery.py --serial emulator-5556 --ssh cp-bcc --output /path/to/new/qa-output
```

## Pemeriksaan AI pada backend aktif

`RemoteAiLifecycleTest` memakai JPEG fixture di emulator API 37. Percobaan pertama menerima deteksi `bottle` dengan band `UNCERTAIN` dan meminta konteks tambahan. Setelah konteks lengkap, gateway menerima `UNAVAILABLE` dengan alasan `LLM_TIMEOUT` dari alur Gemini. [Hasil percobaan pertama](handoff-112/ai-timeout.json)

Pengulangan eksplisit memakai akun baru dan lulus dalam 13,87 detik. Gemini memberi `READY`, dan simpan menambah satu catatan 250 g dengan 10 XP. Pengujian menyimulasikan tanda terima yang hilang setelah write nyata, memulihkan draf terkunci dari disk, lalu mengulang request dengan UUID yang sama setelah deteksi dihapus. Replay mengembalikan catatan awal dan XP tetap 10. Kedua akun probe beserta data terkait telah dibersihkan. Putus jaringan aktual memerlukan penerimaan terpisah. [Log](handoff-112/ai-retry.log), [hasil dan cleanup](handoff-112/ai-retry.json)

## Checkpoint Panduan dan scan dinonaktifkan

`LocalBackendGuideScanTest` lulus dalam 22,06 detik di emulator Android 17/API 37 pada backend Go `8087405` dan PostgreSQL 18 terisolasi di komputer QA. Aplikasi memakai adapter HTTP produksi melalui alamat loopback debug. Satu artikel PUBLISHED dan satu DRAFT dimasukkan ke basis data QA. Artikel uji berisi konten untuk pemeriksaan alur. Katalog produksi tetap berisi nol artikel PUBLISHED saat pemeriksaan ulang.

- Pencarian tanpa hasil lalu ` KARTON ` menemukan artikel terbit. Artikel DRAFT tidak tampil. Detail menunjukkan langkah dan sumber. Aksi Catat mengisi Kertas dan Recycle dengan berat masih kosong dan jumlah catatan tetap nol. [Sumber](handoff-112/local-backend-guide/guide-source.png), [pengisian awal](handoff-112/local-backend-guide/guide-prefill.png)
- Pada versi sebelum perbaikan, tes melewati langkah Panduan lalu gagal menunggu status dinonaktifkan pada sheet. Saat `ai_scan=false`, sheet kini menjelaskan statusnya, kamera tidak dibuka, dan Input manual tetap menuju editor dari Riwayat. [Tampilan scan dinonaktifkan](handoff-112/local-backend-guide/scan-disabled.png), [log lulus](handoff-112/local-backend-guide/native.log)

Untuk mengulang, jalankan backend Go lokal pada `127.0.0.1:18089` dengan database QA yang sudah dimigrasi dan `AI_ENABLED=false`. Impor [artikel fixture](handoff-112/local-backend-guide/guides.json) menggunakan perintah `guides` dari backend pada database QA yang sama. Build dan pasang debug serta APK penguji dengan `-Pwastenot.debugApiBaseUrl=http://10.0.2.2:18089/v1/` dan `-Pwastenot.hotReload=false`. Jalankan tes pada emulator dengan aplikasi dalam keadaan keluar.

```sh
adb shell am instrument -w -e verifyLocalGuideScan true -e class com.example.wastenot.ui.main.LocalBackendGuideScanTest com.example.wastenot.test/com.example.wastenot.WasteNotTestRunner
```

APK debug QA SHA-256 `788448ac2d54951389764e9568d77c6253721cfd080b34abd92ad7663363964f`. APK penguji SHA-256 `e3e3c8d7f0af62f7d64b66860eb6f0213595500685f7e3c10005af1696e948f4`.

## Checkpoint autentikasi dan detail setelah restart

`LocalBackendCoreTest` lulus tiga eksekusi pada emulator Android 17/API 37 dengan backend Go QA dan PostgreSQL yang sama. Form Daftar menampilkan error email duplikat. Form Masuk menampilkan error untuk kata sandi pendek dan salah. Ketiganya mempertahankan root keluar tanpa token refresh lokal. Percobaan dengan kata sandi yang benar mengembalikan identitas akun awal. Basis data mencatat satu akun dan dua sesi yang sesuai dengan daftar dan login sukses.

Pada akun terpisah, UI menyimpan Plastik/Recycle 250 g. Setelah `am force-stop`, proses berbeda memulihkan sesi dan membuka Detail catatan dari Riwayat. ID dan isi catatan sama, dengan satu catatan dan 10 XP. Kedua akun probe dihapus dari basis data QA. [Log pengujian dan hasil basis data](handoff-112/local-backend-core.log)

Untuk mengulang, gunakan konfigurasi build dan backend QA pada checkpoint sebelumnya. Dengan aplikasi keluar, buat satu UUID baru untuk `RUN_ID` dan pilih setiap metode secara berurutan. Jangan hapus data aplikasi antar metode karena pengujian restart memakai sesi yang tersimpan.

```sh
for method in rejectsDuplicateEmailAndInvalidPasswordsWithoutStartingSession savesRecycleRecordBeforeProcessRestart opensSameRecycleRecordAfterProcessRestart; do
    adb shell am force-stop com.example.wastenot
    adb shell am instrument -w -e verifyLocalCore true -e fixtureRunId "$RUN_ID" -e class "com.example.wastenot.ui.main.LocalBackendCoreTest#$method" com.example.wastenot.test/com.example.wastenot.WasteNotTestRunner
done
```

## Checkpoint respons simpan terputus

`LocalBackendCoreTest#replaysCommittedWriteAfterConnectionDrops` lulus dalam 12,11 detik pada emulator API 37 dan backend Go QA. Proxy HTTP loopback menunggu backend mengembalikan 201, lalu menutup koneksi TCP sebelum mengirim respons pertama ke aplikasi. Editor menampilkan status penyimpanan belum diketahui dengan isian 250 g tetap tersedia. Setelah Coba lagi, backend mengembalikan 200 dengan payload permintaan dan ID catatan yang sama. UI menunjukkan konfirmasi tanpa XP tambahan. Basis data mencatat tepat satu catatan 250 g, dashboard tetap 10 XP, dan akun probe dihapus.

Gangguan terkontrol ini memutus satu koneksi TCP. Endpoint HTTPS staging dan perubahan konektivitas seluruh emulator belum diuji pada alur ini. [Hasil belum diketahui](handoff-112/local-write-recovery/unknown-outcome.png), [konfirmasi](handoff-112/local-write-recovery/replayed-write.png), [hasil HTTP dan basis data](handoff-112/local-write-recovery/result.log)

Gunakan backend lokal di port 18089. Build APK debug dan penguji dengan `-Pwastenot.debugApiBaseUrl=http://10.0.2.2:18091/v1/` dan `-Pwastenot.hotReload=false`. Jalankan proxy dengan UUID baru, lalu gunakan UUID yang sama pada tes.

```sh
python3 scripts/local_write_recovery_proxy.py "$RUN_ID"
adb shell am instrument -w -e verifyLocalCore true -e fixtureRunId "$RUN_ID" -e class 'com.example.wastenot.ui.main.LocalBackendCoreTest#replaysCommittedWriteAfterConnectionDrops' com.example.wastenot.test/com.example.wastenot.WasteNotTestRunner
```

## Core

| ID | Status | Bukti dan batas |
| --- | --- | --- |
| AT-01 | Terverifikasi | [Isolasi akun](ACCOUNT_ISOLATION.md) mencakup daftar dan login ulang dari MainActivity dengan akun nyata. |
| AT-02 | Terverifikasi pada QA lokal | [Autentikasi](#checkpoint-autentikasi-dan-detail-setelah-restart) membuktikan error email duplikat, kata sandi pendek dan salah, serta tidak terbentuk sesi. |
| AT-03 | Terverifikasi pada QA lokal | [Detail setelah restart](#checkpoint-autentikasi-dan-detail-setelah-restart) membuktikan satu catatan Plastik/Recycle 250 g dan 10 XP, dengan ID sama setelah force-stop. |
| AT-04 | Terverifikasi pada QA lokal | [Putus koneksi TCP](#checkpoint-respons-simpan-terputus) membuktikan retry manual terhadap commit nyata mempertahankan satu catatan dan 10 XP. [Pengulangan AI](#pemeriksaan-ai-pada-backend-aktif) juga menguji pemulihan draf dari disk dengan simulasi tanda terima hilang pada adapter. |
| AT-05–06 | Parsial | `QuantityTest`, `EditorDraftTest` dan `QuantityRequestFieldsTest` menguji validasi model, form dan serialisasi. Audit penolakan HTTP untuk setiap input batas. |
| AT-07 | Terverifikasi | [Dataset PRD](PRD_DATASET_LIVE.md) mencatat Reduce 2 terpisah dari total penanganan. |
| AT-08–09 | Parsial | [Ringkasan backend](BACKEND_SUMMARIES.md) dan [fixture Statistik](STATISTICS.md) mencakup agregat serta unit terpisah. Ulangi dataset Reduce saja dan campuran unit lewat UI dengan akun nyata. |
| AT-10 | Terverifikasi | [PR #113](PRD_DATASET_LIVE.md) membuktikan 650 g, 1.400 g, 50%, 50 XP, streak 2 pada dua tema. |
| AT-11–12 | Parsial | [Riwayat API](HISTORY_API.md) membuktikan filter server, cursor dan Back dengan 22 catatan. `HistoryViewModelTest` menguji kombinasi filter dan respons terlambat. Jalankan skenario kombinasi dan race dari UI. |
| AT-13 | Terverifikasi | [Konflik versi nyata](#checkpoint-sesi-dan-konflik-versi) membuktikan 250→300 g pada ID yang sama dan 10 XP tetap. |
| AT-14 | Parsial | [Perjalanan AI](AI_MAIN_REGRESSION.md) membuktikan hapus mengembalikan agregat/progres ke nol. Ulangi streak multi-hari sesudah penghapusan. |
| AT-15 | Terverifikasi | [Konflik versi nyata](#checkpoint-sesi-dan-konflik-versi) membuktikan 409, D04 dan draf bertahan sampai Simpan eksplisit. |
| AT-16–17 | Terverifikasi | [Isolasi akun](ACCOUNT_ISOLATION.md) mencakup enam akses silang 404, dua sesi dan draf/stack per akun pada MainActivity. |
| AT-18 | Parsial | [Checkpoint sesi](#checkpoint-sesi-dan-konflik-versi) membuktikan 401 akibat generation lama, satu rotasi dan retry yang berhasil. Expiry waktu JWT alami masih terbuka. |
| AT-19 | Terverifikasi untuk revocation | [Checkpoint sesi](#checkpoint-sesi-dan-konflik-versi) membuktikan root kembali ke Masuk setelah revocation dan kredensial dihapus. |
| AT-20 | Terverifikasi pada QA lokal | [Tes backend QA](#checkpoint-panduan-dan-scan-dinonaktifkan) mencakup kapitalisasi, hasil kosong, dan detail artikel PUBLISHED. [Panduan](GUIDE.md) mencakup clear query. Katalog produksi masih kosong. |
| AT-21 | Terverifikasi | [Dataset PRD](PRD_DATASET_LIVE.md) membuktikan periode 7/30, rentang, agregat dan chart. |
| AT-22 | Terverifikasi dengan jam terkontrol | [Pergantian hari](ACCOUNT_DAY_ROLLOVER.md) membuktikan refresh tanggal akun, periode, draf dan status streak pada respons terkontrol. |
| AT-23 | Parsial | [Pemulihan write](WRITE_RECOVERY.md) membuktikan PID berubah dan draf/UUID tetap tersedia pada fixture. [Isolasi](ACCOUNT_ISOLATION.md) membuktikan kepemilikan draf dengan akun nyata. Tambahkan pemeriksaan password dan process death dalam alur remote yang sama. |
| AT-24–25 | Parsial | [TalkBack Masuk](#checkpoint-talkback-masuk) memverifikasi fokus dan validasi pada font 200% dengan Gboard aktif. [Footer/tema](SECONDARY_SCREEN_REVIEW.md), [fokus keyboard](KEYBOARD_FOCUS.md), dan [editor](EDITOR.md) memberi bukti terpisah. Verifikasi ucapan dan kombinasi landscape/IME/font 200% untuk perjalanan lain masih terbuka. |
| AT-26 | Parsial | [Riwayat API](HISTORY_API.md) membuktikan 22 catatan backend di MainActivity. [Benchmark native](#checkpoint-riwayat-1000-catatan) mencatat jank dan PSS untuk 1.000 catatan fixture. Pengukuran perangkat fisik dan pagination 1.000 catatan backend masih terbuka. |

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
| AT-AI-12 | Parsial | `ScanViewModelTest` mencakup timeout, Retry-After dan jalur manual eksplisit. [Uji backend](#pemeriksaan-ai-pada-backend-aktif) mengamati `LLM_TIMEOUT` nyata dan keberhasilan pengulangan eksplisit. Uji putus jaringan pada perangkat/staging. |
| AT-AI-13 | Parsial | [Regresi nyata](AI_MAIN_REGRESSION.md) membuktikan 250 g, satu log/10 XP dan provenance. [Transport](AI_TRANSPORT.md) mencakup replay setelah acknowledgment hilang. Uji dua aksi simpan dalam alur UI yang sama. |
| AT-AI-14 | Parsial | [Transport](AI_TRANSPORT.md) memvalidasi foreign/expired dan [review scan](SCAN_REVIEW_FIXES.md) menguji expiry/manual. Uji 404/410/409 dari layanan dengan dua akun dan referensi yang sudah dipakai. |
| AT-AI-15–16 | Parsial | `ScanViewModelTest`, `ScanPhotoCacheTest` dan [transport](AI_TRANSPORT.md) mencakup koordinat, expiry dan cleanup lokal. Uji file rusak/besar, orientasi kamera fisik, serta deadline retensi setelah crash. |
| AT-AI-17 | Parsial | [Dua tema](SCAN_THEMES.md) dan [daftar objek](SCAN_CHOICE_ORDER.md) membuktikan pilihan melalui daftar. Uji TalkBack lisan, 200%, keyboard dan landscape pendek bersama. |
| AT-AI-18 | Terverifikasi pada QA lokal | [Tes backend QA](#checkpoint-panduan-dan-scan-dinonaktifkan) mencakup `ai_scan=false`, autentikasi, Riwayat dan input manual saat kamera dinonaktifkan. |

## Penyimpanan aktivitas

`RemoteActivityStore` membaca aktivitas melalui `CoreRepository`. Tema disimpan oleh DataStore, draf memakai SharedPreferences per akun, dan foto sementara memakai file lokal. Riwayat setelah cold start offline memerlukan keputusan terpisah tentang Room per pemilik, sumber pembacaan UI, antrean sinkronisasi, retry, resolusi versi, logout dan penghapusan data.
