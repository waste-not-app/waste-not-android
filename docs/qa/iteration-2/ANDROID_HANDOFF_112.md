# Audit penerimaan Android #112

Pembaruan 11 November 2026. Baseline `b772040`, hasil merge PR #113 ke `main`. Inventaris sumber mencakup **20 layar wajib dan 4 dialog**. Tabel menautkan bukti pada konfigurasi dan revisi masing-masing. Status parsial berarti masih ada bagian skenario yang perlu diuji. S20 mendeskripsikan state lintas layar.

## Layar dan navigasi

| Layar | Bukti yang tersedia | Review yang masih dibutuhkan |
| --- | --- | --- |
| S01–S03 | [Startup](STARTUP.md), [Masuk/Daftar](NAV_AUTH.md), [footer daftar](SECONDARY_SCREEN_REVIEW.md), [refresh dan revocation pada root](#checkpoint-sesi-dan-konflik-versi), [TalkBack pada Masuk](#checkpoint-talkback-masuk), [TalkBack membuka Daftar](#checkpoint-talkback-workspace) | Verifikasi ucapan dan validasi form Daftar. |
| S04–S06, S08–S09 | [Bagian bawah konten](SCROLLED_CONTENT.md), [editor](EDITOR.md), [simpan](SAVE.md), [detail/Back](DETAIL.md), [konflik versi nyata](#checkpoint-sesi-dan-konflik-versi), [pemulihan koneksi di QA](#checkpoint-respons-simpan-terputus) | Ulangi pemulihan koneksi pada lingkungan HTTPS staging. |
| S07, S10 | [Filter, halaman berikut, Back dan refresh](HISTORY_API.md), [pagination 1.000 catatan Go QA dan baseline emulator](#checkpoint-riwayat-1000-catatan) | Profil ulang di perangkat fisik pada build yang representatif untuk distribusi jika cakupan perangkat diperluas. |
| S11–S12 | [Katalog produksi kosong](GUIDE_API.md), [pencarian dan prefill](GUIDE.md), [artikel PUBLISHED pada backend QA lokal](#checkpoint-panduan-dan-scan-dinonaktifkan). Pemeriksaan ulang 11 November menemukan 0 artikel PUBLISHED di produksi. | Penerimaan konten saat artikel terbit tersedia di katalog produksi. |
| S13–S14, S16 | [Statistik dan Profil dengan dataset PRD](PRD_DATASET_LIVE.md), [tema](THEME_DATASTORE.md), [review callout S14](#checkpoint-s14), [fokus TalkBack pada angka chart dan streak](#checkpoint-talkback-workspace) | Verifikasi ucapan pada pengaturan dan perjalanan lengkap. |
| S21–S25 | [Dua tema hingga akhir scroll](SCAN_THEMES.md), [kamera aktif](CAMERA_LIFECYCLE.md), [saran dan fallback](SCAN_REVIEW_FIXES.md), [kamera ke simpan nyata](AI_MAIN_REGRESSION.md), [capability off](#checkpoint-panduan-dan-scan-dinonaktifkan), [gangguan koneksi deteksi di QA](#checkpoint-gangguan-deteksi-ai), [gangguan rekomendasi dan Reduce](handoff-112/local-scan-recovery/recommendation.log) | Ulangi gangguan lewat HTTPS staging. Uji kamera fisik ditunda sesuai cakupan emulator. |
| D01–D04 | [Delete](DETAIL.md), [keluar dan buang draf](EXIT_DIALOGS.md), [pemulihan write](WRITE_RECOVERY.md), [D04 dengan backend](#checkpoint-sesi-dan-konflik-versi) | Uji fokus TalkBack kembali ke pemicu setelah Back/batal. |
| Dock, header, dan sheet | [Navigasi](SCREENS_NAVIGATION.md), [transisi](NAVIGATION_MOTION.md), [fokus keyboard](KEYBOARD_FOCUS.md), [TalkBack membuka dan menutup sheet dari lima tab](#checkpoint-talkback-workspace) | Uji fokus lisan lintas dialog lain, rotasi pendek, serta CTA dengan IME terbuka. |

Regresi pada API 35 menjalankan 15 metode `ScreenKitReviewTest`, `SessionRootTest` dan dua kasus `ScanSheetTest`. Empat belas lulus, termasuk scroll konten, dock, pemulihan sesi terkontrol, expiry scan dan jalur manual. Satu tes gabungan editor/filter/dialog berhenti saat Android menolak injeksi sentuhan ke window. Metode yang sama lulus saat dijalankan sendiri pada API 37 dalam 60,15 detik.

Regresi lanjutan di API 37 lulus 18 tes. Ini mencakup pemulihan sesi dan submit IME, alternatif teks grafik dan unit jumlah, pilihan objek/material, expiry, serta langkah dan sumber saat saran AI belum tersedia. [Hasil native](handoff-112/followup-emulator.log)

## Checkpoint TalkBack Masuk

Di emulator Android 17/API 37, font 200%, 1080×2400 px dan 420 dpi, tes lama terkadang mulai mencari tombol saat fokus sudah pindah ke Gboard. Probe pada APK aplikasi awal juga lulus ketika IME belum terbuka. Saat Gboard terbuka, tombol dapat terlihat di viewport aplikasi ketika TalkBack sedang menelusuri window keyboard.

`TalkBackNavigationTest` membuka IME dan menunggu sampai terlihat, lalu mengembalikan fokus aksesibilitas ke kontrol Tampilkan kata sandi. TalkBack 17 dengan Enhanced keymap harus mencapai tombol Masuk yang dapat diaktifkan pada langkah maju berikutnya. Tes mengaktifkan tombol, memeriksa pesan validasi kata sandi di window aplikasi, lalu memakai langkah mundur untuk mencapai pesan di atas tombol dan langkah maju untuk kembali ke Masuk. Perubahan ada pada driver pengujian. [Screenshot](handoff-112/talkback-masuk-font200.png), [hasil native awal](handoff-112/talkback-masuk-native.log), [pintasan TalkBack](https://support.google.com/accessibility/android/answer/6110948?hl=en)

Pada pengulangan dengan perekam emulator, driver mencari kolom kata sandi pada window aplikasi setelah pengisian email dan menunggu fokus stabil sesudah perubahan IME. Perekam menangkap Masuk dengan peran tombol serta validasi panjang kata sandi. Transkripsi lokal otomatis mengenali keduanya dengan beberapa kesalahan ejaan. Bukti ini mencakup Masuk dan pesan validasi, dengan Gboard terbuka dan font 200%. Pemeriksaan ucapan pada layar lain masih terbuka. [Hasil rekaman dan transkripsi](handoff-112/talkback-masuk-recording.log)

Pada emulator QA dengan TalkBack Enhanced aktif, font 200%, Gboard dan kedua APK terpasang, jalankan perintah berikut. Untuk merekam, jalankan perekam emulator secara terpisah dan tambahkan `-e talkbackRecordSpeech true` agar fokus menunggu saat pengumuman diputar.

```sh
adb shell settings put secure show_ime_with_hard_keyboard 1
adb shell am instrument -w -e talkback true -e talkbackKeymap enhanced -e class com.example.wastenot.ui.main.TalkBackNavigationTest com.example.wastenot.test/com.example.wastenot.WasteNotTestRunner
```

### Masuk di landscape

Pada build awal, area form yang tersisa saat Gboard terbuka pada font 200% bisa memotong tombol Masuk di bawah kata sandi. Di layout landscape, tombol sekarang berada di samping kata sandi dan sejajar dengan bagian bawah pesan validasi. Grup traversal menjaga urutan kolom kata sandi, kontrol visibilitas, validasi, lalu Masuk. Saat validasi kata sandi muncul, layar melepas fokus input, memberi Android waktu menyelesaikan antrean perubahan IME, lalu menutup Gboard dan menggulir seperlunya. Pengguna dapat memilih kembali kolom kata sandi untuk mengoreksi isian. [Dokumentasi traversal Compose](https://developer.android.com/develop/ui/compose/accessibility/traversal)

Enam pengulangan berturut-turut lulus di emulator Android 17/API 37 dengan font 200%, termasuk dua pengulangan dengan jeda perekaman ucapan. Tes memastikan Activity benar-benar landscape. Dengan Gboard terbuka, satu langkah TalkBack dari Tampilkan kata sandi harus mencapai Masuk. Aktivasi menampilkan validasi kata sandi dan menutup Gboard. Tes memfokuskan teks validasi yang terlihat melalui aksi aksesibilitas, kemudian kembali ke Masuk dengan pintasan maju TalkBack. [Fokus setelah validasi](handoff-112/talkback-masuk-landscape-font200.png), [hasil native](handoff-112/talkback-masuk-landscape.log)

```sh
adb shell wm user-rotation lock 1
adb shell am instrument -w -e talkback true -e talkbackKeymap enhanced -e talkbackLandscape true -e class com.example.wastenot.ui.main.TalkBackNavigationTest com.example.wastenot.test/com.example.wastenot.WasteNotTestRunner
adb shell wm user-rotation free
```

## Checkpoint TalkBack workspace

`TalkBackNavigationTest#readerOpensRegistrationAndNavigatesWorkspace` lulus dalam 38,82 detik pada Android 17/API 37, TalkBack 17 Enhanced, dan font 200% di portrait. TalkBack mencapai judul dan kolom Nama pada Daftar, kemudian mengaktifkan kelima tab pada akun Go QA. Angka harian Statistik menampilkan 250 g dan 0 benda. Fokus juga mencapai status streak pada Profil.

Sebelum perbaikan, label Catat atau scan sampah terpisah dari node yang menerima klik. Tombol kini mengekspos label dan aksi pada satu kontrol. Dari setiap tab, TalkBack mencapai tombol dengan navigasi dock, membuka sheet scan dengan capability nonaktif, menutup sheet, dan kembali ke pemicu dengan tab yang sama tetap terpilih. Tes mengukur fokus dan aksi. Rekaman ucapan workspace masih terbuka. [Log TalkBack](handoff-112/talkback-workspace.log).

```sh
adb shell am instrument -w -e talkback true -e talkbackKeymap enhanced -e verifyLocalTalkBack true -e fixtureRunId "$RUN_ID" -e class 'com.example.wastenot.ui.main.TalkBackNavigationTest#readerOpensRegistrationAndNavigatesWorkspace' com.example.wastenot.test/com.example.wastenot.WasteNotTestRunner
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

Tes lanjutan pada API 37 memakai adapter HTTP aplikasi dan Go/PostgreSQL QA. Akun sekali pakai diisi 1.000 baris pada satu tanggal. Aksi Muat catatan berikutnya mengambil seluruh 50 halaman, UI menampilkan 1.000 catatan, dan daftar tetap bisa digulir. Tes native lulus dalam 18,56 detik. Fase paging dan scroll memerlukan 13,34 detik. PSS naik dari 113.909 ke 124.052 KiB. Dari 262 frame, 156 tercatat jank (59,54%) dan p95 93 ms pada renderer perangkat lunak emulator. Pengukuran ini mengikat skenario emulator. Akun dan seluruh baris uji sudah dibersihkan. [Tangkapan layar](handoff-112/history-go-1000/history-go-1000.png), [hasil](handoff-112/history-go-1000/result.txt), [frame](handoff-112/history-go-1000/gfxinfo.txt).

Gunakan APK debug dengan `http://10.0.2.2:18089/v1/`. Jalankan `LocalBackendCoreTest#paginatesOneThousandRecordsFromGoBackend` dengan `-e verifyLocalCore true -e fixtureRunId "$RUN_ID"`. Setelah akun muncul, jalankan [seed SQL](handoff-112/history-go-1000/seed.sql) dengan `psql -v run_id="$RUN_ID" -f seed.sql` pada PostgreSQL QA lokal dalam 40 detik.

`LocalBackendCoreTest#newerCombinedHistoryFilterWinsWhenOlderResponseArrivesLate` lulus dalam 27,49 detik. Proxy menahan HTTP 200 filter Plastik, kemudian mengirim hasil Kertas + Recycle lebih dulu. Setelah respons Plastik dilepas, hanya catatan Kertas + Recycle tampil. Pengujian berikutnya memilih Plastik + Recycle dan memastikan Plastik/Trash tidak muncul. Gunakan gateway QA port 18094 dari checkpoint gangguan deteksi dan argumen `verifyLocalCore`. [Hasil dan urutan HTTP](handoff-112/history-go-1000/filter-race.log), [layar hasil](handoff-112/history-go-1000/filter-race.png).

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

## Checkpoint gangguan deteksi AI

`LocalScanRecoveryTest` lulus dalam 21,05 detik di emulator Android 17/API 37. Aplikasi memakai CameraX, adapter HTTP produksi, dan gateway Go QA dengan worker deteksi terkontrol dari contoh kontrak backend. Proxy memutus respons deteksi pertama setelah Go menyelesaikan HTTP 201. UI membuka fallback, Coba lagi kembali ke tinjau foto, dan aksi Scan foto berikutnya memakai UUID baru dan mencapai Periksa objek. Untuk upload ketiga, proxy menahan respons 201 selama delapan detik. Tes menutup sheet ketika deteksi masih berjalan. Setelah respons dilepas, alur tetap tertutup dan pembukaan berikutnya mulai dari kamera. Dashboard tetap nol catatan dan nol XP. Akun uji dihapus, termasuk operasi deteksi yang sempat tersimpan. [Fallback](handoff-112/local-scan-recovery/detection-network-error.png), [hasil retry](handoff-112/local-scan-recovery/detection-retry.png), [log native](handoff-112/local-scan-recovery/native.log), [log proxy](handoff-112/local-scan-recovery/proxy.log).

`TalkBackNavigationTest#readerSelectsSecondScanCandidateFromList` memakai worker fixture dua kandidat. Pada font 200%, TalkBack mengubah pilihan dari skor 90% ke 80% melalui daftar. Pada landscape, tes juga mencapai Input manual. Fixture hanya menghasilkan kandidat deteksi untuk menguji pilihan dan aksesibilitas. Pengumuman audio kandidat masih perlu diperiksa. [Log portrait dan landscape](handoff-112/local-scan-recovery/talkback-candidates.log).

Sebelas metode utama Go `8087405` lulus di PostgreSQL QA. Cakupannya meliputi batas input, akses dan expiry AI, publikasi panduan, penghapusan saat generasi, serta pemulihan operasi yang terputus. [Log backend](handoff-112/local-scan-recovery/backend.log).

Untuk mengulang, jalankan [fixture loopback](../../../scripts/local_scan_recovery_fixture.py) dengan direktori `contracts/ai/examples` dari backend. Jalankan Go lokal pada port 18093 dengan database QA terisolasi, `AI_ENABLED=true`, `AI_RECOMMENDATION_ENABLED=false`, `AI_ALLOW_PROTOTYPE=true`, kedua URL worker ke `http://127.0.0.1:18092`, dan dua token lokal yang berbeda. Build debug dengan `-Pwastenot.debugApiBaseUrl=http://10.0.2.2:18094/v1/`, pasang APK penguji, dan jalankan dengan kamera emulator diizinkan, aplikasi keluar, serta argumen berikut. Mulai ulang proxy untuk mengulang urutan gangguan.

```sh
adb shell am instrument -w -e verifyLocalScanRecovery true -e fixtureRunId "$RUN_ID" -e class com.example.wastenot.ui.scan.LocalScanRecoveryTest com.example.wastenot.test/com.example.wastenot.WasteNotTestRunner
```

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

### Batas input, ringkasan, dan penghapusan

`LocalBackendCoreTest#goRejectsQuantityAndDateOutsideSupportedLimits` lulus di emulator API 37 dengan Go QA. API menolak berat nol, negatif, terlalu besar dan pecahan gram, jumlah nol, terlalu besar dan pecahan, berat/jumlah kosong atau bercampur, serta tanggal di luar rentang. Empat nilai batas yang sah tersimpan dengan satuannya sendiri pada hari ke-30. Tidak ada catatan dari permintaan yang ditolak.

`LocalBackendCoreTest#statisticsSeparatesUnitsAndDeleteRecomputesMultiDayStreak` lulus dalam 8,02 detik. Dataset Reduce saja menampilkan pesan rate belum tersedia, sementara Go mengembalikan rate null dan dua benda dihindari. Statistik dataset campuran menampilkan 500 g dan tiga benda secara terpisah. Menghapus catatan hari tengah dari UI menurunkan streak 3→1 dan XP 40→30. [Bukti UI dan hasil](handoff-112/local-core-summary/).

Pasangan `persistsUnsubmittedDraftBeforeProcessRestart` dan `restoresUnsubmittedDraftForSameAccountAfterProcessRestart` lulus dengan akun Go QA, sesi perangkat yang sama, dan PID berbeda setelah force-stop. Draf pulih dengan isian dan UUID request yang sama. Jumlah catatan server tetap nol. Setelah keluar, field kata sandi pada Masuk kosong. [Log dua proses](handoff-112/local-core-summary/process-restart.log).

`LocalBackendCoreTest#accessTokenExpiresOnItsOwnAndOneRefreshRestoresTheRequest` lulus dalam 918,486 detik. JWT Go QA berlaku 900 detik sesuai klaim yang ditandatangani. Setelah kedaluwarsa alami, satu permintaan mendapat 401, satu refresh berhasil, lalu permintaan ulang mendapat 200. Akun dan root tetap aktif. PostgreSQL mengonfirmasi generation 2 dan hanya satu refresh token yang sudah digunakan. [Log expiry](handoff-112/local-core-summary/access-expiry.log)

Jalankan metode expiry dengan `verifyLocalCore=true`, `verifyNaturalExpiry=true`, dan `fixtureRunId` unik pada APK debug port 18089. Untuk [pemulihan rekomendasi](handoff-112/local-scan-recovery/recommendation.log), gunakan APK port 18094, jalankan proxy dengan `--recommendation-recovery`, dan aktifkan rekomendasi serta hasil worker fixture pada Go QA port 18093. Jalankan metode `LocalScanRecoveryTest#recoversRecommendationAndSavesReduceWithoutProvenance` dengan `verifyLocalRecommendationRecovery=true` dan UUID baru. Pengujian ini memakai HTTP loopback.

| ID | Status | Bukti dan batas |
| --- | --- | --- |
| AT-01 | Terverifikasi | [Isolasi akun](ACCOUNT_ISOLATION.md) mencakup daftar dan login ulang dari MainActivity dengan akun nyata. |
| AT-02 | Terverifikasi pada QA lokal | [Autentikasi](#checkpoint-autentikasi-dan-detail-setelah-restart) membuktikan error email duplikat, kata sandi pendek dan salah, serta tidak terbentuk sesi. |
| AT-03 | Terverifikasi pada QA lokal | [Detail setelah restart](#checkpoint-autentikasi-dan-detail-setelah-restart) membuktikan satu catatan Plastik/Recycle 250 g dan 10 XP, dengan ID sama setelah force-stop. |
| AT-04 | Terverifikasi pada QA lokal | [Putus koneksi TCP](#checkpoint-respons-simpan-terputus) membuktikan retry manual terhadap commit nyata mempertahankan satu catatan dan 10 XP. [Pengulangan AI](#pemeriksaan-ai-pada-backend-aktif) juga menguji pemulihan draf dari disk dengan simulasi tanda terima hilang pada adapter. |
| AT-05–06 | Terverifikasi pada Go QA dan emulator | `QuantityTest`, `EditorDraftTest` dan `QuantityRequestFieldsTest` menguji validasi model, form dan serialisasi. [Uji HTTP batas](#batas-input-ringkasan-dan-penghapusan) membuktikan respons 400/422 dan batas yang sah. |
| AT-07 | Terverifikasi | [Dataset PRD](PRD_DATASET_LIVE.md) mencatat Reduce 2 terpisah dari total penanganan. |
| AT-08–09 | Terverifikasi pada Go QA dan emulator | [Ringkasan Go QA](#batas-input-ringkasan-dan-penghapusan) membuktikan Reduce saja dengan rate null dan campuran berat/jumlah dengan satuan terpisah. |
| AT-10 | Terverifikasi | [PR #113](PRD_DATASET_LIVE.md) membuktikan 650 g, 1.400 g, 50%, 50 XP, streak 2 pada dua tema. |
| AT-11–12 | Terverifikasi pada Go QA dan emulator | [Riwayat API](HISTORY_API.md) membuktikan filter server, cursor dan Back dengan 22 catatan. [Uji respons terlambat](#checkpoint-riwayat-1000-catatan) membuktikan kombinasi kategori/metode dan hasil tetap terikat filter terbaru. |
| AT-13 | Terverifikasi | [Konflik versi nyata](#checkpoint-sesi-dan-konflik-versi) membuktikan 250→300 g pada ID yang sama dan 10 XP tetap. |
| AT-14 | Terverifikasi pada Go QA dan emulator | [Penghapusan lewat UI](#batas-input-ringkasan-dan-penghapusan) menurunkan XP 40→30 dan streak 3→1. |
| AT-15 | Terverifikasi | [Konflik versi nyata](#checkpoint-sesi-dan-konflik-versi) membuktikan 409, D04 dan draf bertahan sampai Simpan eksplisit. |
| AT-16–17 | Terverifikasi | [Isolasi akun](ACCOUNT_ISOLATION.md) mencakup enam akses silang 404, dua sesi dan draf/stack per akun pada MainActivity. |
| AT-18 | Terverifikasi pada Go QA dan emulator | [Log expiry](handoff-112/local-core-summary/access-expiry.log) membuktikan JWT kedaluwarsa alami setelah 15 menit, satu refresh, lalu permintaan yang sama berhasil tanpa keluar dari akun. [Checkpoint sesi](#checkpoint-sesi-dan-konflik-versi) mencakup 401 akibat generation lama. |
| AT-19 | Terverifikasi untuk revocation | [Checkpoint sesi](#checkpoint-sesi-dan-konflik-versi) membuktikan root kembali ke Masuk setelah revocation dan kredensial dihapus. |
| AT-20 | Terverifikasi pada QA lokal | [Tes backend QA](#checkpoint-panduan-dan-scan-dinonaktifkan) mencakup kapitalisasi, hasil kosong, dan detail artikel PUBLISHED. [Panduan](GUIDE.md) mencakup clear query. Katalog produksi masih kosong. |
| AT-21 | Terverifikasi | [Dataset PRD](PRD_DATASET_LIVE.md) membuktikan periode 7/30, rentang, agregat dan chart. |
| AT-22 | Terverifikasi dengan jam terkontrol | [Pergantian hari](ACCOUNT_DAY_ROLLOVER.md) membuktikan refresh tanggal akun, periode, draf dan status streak pada respons terkontrol. |
| AT-23 | Terverifikasi pada Go QA dan emulator | [Restart dua proses](#batas-input-ringkasan-dan-penghapusan) memulihkan draf dan UUID untuk akun yang sama serta memeriksa field kata sandi kosong setelah keluar. [Isolasi](ACCOUNT_ISOLATION.md) membuktikan kepemilikan draf dengan dua akun nyata. |
| AT-24–25 | Parsial | [TalkBack Masuk](#checkpoint-talkback-masuk) memverifikasi fokus, aktivasi dan pengumuman Masuk serta validasi pada font 200% dengan Gboard aktif. [Footer/tema](SECONDARY_SCREEN_REVIEW.md), [fokus keyboard](KEYBOARD_FOCUS.md), dan [editor](EDITOR.md) memberi bukti terpisah. Verifikasi ucapan dan kombinasi landscape/IME/font 200% untuk perjalanan lain masih terbuka. |
| AT-26 | Terverifikasi di emulator | [Benchmark native](#checkpoint-riwayat-1000-catatan) mencatat jank dan PSS untuk 1.000 catatan pada backend Go QA. Fixture terpisah membuktikan error halaman berikut, pemeliharaan halaman awal dan retry cursor yang sama. |

## Scan AI

| ID | Status | Bukti dan batas |
| --- | --- | --- |
| AT-AI-01 | Terverifikasi di emulator | [TalkBack workspace](#checkpoint-talkback-workspace) membuktikan tab dan fokus kembali ke pemicu dari kelima tab. [Fokus keyboard](KEYBOARD_FOCUS.md) mencakup jalur keyboard, dan [navigasi kamera](CAMERA_LIFECYCLE.md) mencakup sheet aktif. |
| AT-AI-02 | Terverifikasi di emulator | [Lifecycle kamera](CAMERA_LIFECYCLE.md) mencakup dua penolakan izin, tombol Pengaturan setelah izin diblokir, dan pemulihan tanpa membuka kamera otomatis. [Emulator tanpa kamera](handoff-112/local-scan-recovery/no-camera.log) membuktikan pesan kesalahan dan Input manual setelah izin diberikan. |
| AT-AI-03–04 | Parsial | [Lifecycle kamera](CAMERA_LIFECYCLE.md), [transport](AI_TRANSPORT.md) dan [regresi nyata](AI_MAIN_REGRESSION.md) mencakup capture, upload setelah tinjau, lifecycle, dan recreate. [Go QA](#checkpoint-gangguan-deteksi-ai) membuktikan cancel saat deteksi aktif. Uji retake saat operasi aktif dan process death dengan foto hilang. |
| AT-AI-05 | Terverifikasi di emulator | [TalkBack daftar kandidat](#checkpoint-gangguan-deteksi-ai) membuktikan satu pilihan dan [urutan kandidat](SCAN_CHOICE_ORDER.md) membuktikan konfirmasi eksplisit. |
| AT-AI-06 | Parsial | [Transport](AI_TRANSPORT.md) mempertahankan band server. Verifikasi batas skor/unknown dengan respons layanan yang terkontrol. |
| AT-AI-07 | Terverifikasi | [Regresi nyata](AI_MAIN_REGRESSION.md) meminta konfirmasi material untuk `bottle` meskipun skornya tinggi. |
| AT-AI-08 | Terverifikasi pada Go QA dan emulator | [Gangguan rekomendasi](handoff-112/local-scan-recovery/recommendation.log) membuktikan respons dari kondisi lama diabaikan setelah pengguna kembali ke objek dan mengubah kondisi. Permintaan baru membawa revisi baru dan membuka saran. |
| AT-AI-09–10 | Parsial | [Transport](AI_TRANSPORT.md) memvalidasi identitas rekomendasi dan membedakan special handling. [Policy-08](POLICY08_ANDROID.md) membuktikan tampilan langkah/sumber terkontrol. Periksa kasus layanan nyata dengan kebijakan backend yang berlaku. |
| AT-AI-11 | Terverifikasi pada Go QA dan emulator | [Reduce manual](handoff-112/local-scan-recovery/recommendation.log) membuktikan UI mewajibkan tindakan manual eksplisit sebelum menyimpan dua benda. Go menyimpan satu catatan/10 XP tanpa provenance. |
| AT-AI-12 | Parsial | `ScanViewModelTest` mencakup timeout, Retry-After dan jalur manual eksplisit. Go QA dan emulator membuktikan retry deteksi dan [retry rekomendasi](handoff-112/local-scan-recovery/recommendation.log) setelah respons putus. [Uji backend](#pemeriksaan-ai-pada-backend-aktif) mengamati `LLM_TIMEOUT` nyata. Gangguan pada HTTPS staging masih terbuka. |
| AT-AI-13 | Parsial | [Regresi nyata](AI_MAIN_REGRESSION.md) membuktikan 250 g, satu log/10 XP dan provenance. [Transport](AI_TRANSPORT.md) mencakup replay setelah acknowledgment hilang. Uji dua aksi simpan dalam alur UI yang sama. |
| AT-AI-14 | Terverifikasi pada Go QA dan tes UI terkontrol | `TestAIIntegration` membuktikan 404 dari akun kedua, 410 setelah sesi dipensiunkan, dan 409 saat rekomendasi dipakai untuk catatan kedua. [Review scan](SCAN_REVIEW_FIXES.md) menguji tindakan manual pada UI Android. [Log Go](handoff-112/local-scan-recovery/backend.log) |
| AT-AI-15–16 | Parsial | `ScanViewModelTest`, `ScanPhotoCacheTest` dan [transport](AI_TRANSPORT.md) mencakup koordinat, expiry dan cleanup lokal. Go menolak file rusak, MIME keliru, dan file di atas batas dengan 422/415/413, serta membersihkan operasi kedaluwarsa dan pekerjaan yang tertinggal setelah crash. Orientasi kamera dan bukti retensi raw image setelah crash masih terbuka. [Log Go](handoff-112/local-scan-recovery/backend.log) |
| AT-AI-17 | Terverifikasi di emulator | [TalkBack daftar kandidat](#checkpoint-gangguan-deteksi-ai) lulus pada 200% di portrait dan landscape, termasuk pemilihan tanpa bbox dan akses Input manual di landscape. Bukti ucapan tetap dilacak pada AT-24–25. |
| AT-AI-18 | Terverifikasi pada QA lokal | [Tes backend QA](#checkpoint-panduan-dan-scan-dinonaktifkan) mencakup `ai_scan=false`, autentikasi, Riwayat dan input manual saat kamera dinonaktifkan. |

## Penyimpanan aktivitas

`RemoteActivityStore` membaca aktivitas melalui `CoreRepository`. Tema disimpan oleh DataStore, draf memakai SharedPreferences per akun, dan foto sementara memakai file lokal. Riwayat setelah cold start offline memerlukan keputusan terpisah tentang Room per pemilik, sumber pembacaan UI, antrean sinkronisasi, retry, resolusi versi, logout dan penghapusan data.
