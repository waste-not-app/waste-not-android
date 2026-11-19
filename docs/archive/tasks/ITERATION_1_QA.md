# Iteration 1: bukti Android

> Koreksi penerimaan visual, 19 Oktober 2026: hasil fungsi/perangkat di bawah tetap merupakan bukti historis, tetapi label Purpose/Craft PASS tidak membuktikan kesesuaian mockup. Pengguna mengonfirmasi komposisi layar dan navbar belum sesuai. Penerimaan visual dibuka kembali dan dilanjutkan pada [Iteration 2](ITERATION_2_DESIGN.md).

Tanggal pengujian: 15 Oktober 2026. Branch: `codex/android-auth-navigation`, ditumpuk di atas `codex/android-theme-forms` (PR #94). Dua belas task berada di Iteration 1, 13–26 Oktober. Seluruh issue Android ditugaskan kepada `oktavsm`.

## Batas penerimaan

Arahan pengguna mengizinkan dummy/placeholder untuk ketergantungan backend dan AI/ML. Mode **Lihat data contoh** memakai sumber terpisah, berlabel, tanpa HTTP, token, atau persistensi akun. Data contoh mengikuti PRD §9.4, bertanggal 21 September 2026; angka tidak dipresentasikan sebagai data hari ini atau pengguna nyata.

Adapter produksi tetap memakai SessionRepository/CoreRepository. Endpoint write aktivitas dan ringkasan live belum terintegrasi: UI menjelaskan batasnya, mempertahankan draf saat gagal, dan tidak memalsukan hasil sukses. Panduan menampilkan placeholder konten. Tidak ada kamera/inference simulasi; Catat tetap membuka form manual. Integrasi server B03/B04 dan endpoint fitur berikutnya harus diuji tersendiri.

## Pengujian perangkat minimum

APK native dibangun dengan `-Pwastenot.hotReload=false`, API minimum 24. HotSwan development menaikkan minimum debug menjadi 26; hasil native di bawah tidak diklaim sebagai pengujian runtime interpreter HotSwan.

Emulator khusus QA Android 7.0/API 24 x86_64, 1080 × 1920, density 480 (360 dp). Tidak memakai akun GitHub/Google atau credential produksi pada aplikasi. Login/register sukses, error credential, serta pemulihan sesi memakai gateway fixture; contoh catatan memakai DemoActivityStore.

| Pemeriksaan | Bukti |
|---|---|
| Suite native 360 dp, font normal | **20 tes lulus**, 165,069 detik; Keystore/container, tema, kontrol, navigasi dan sesi |
| Navigasi/session/kalender, 360 dp, font 200% | **9 tes lulus**, 130,788 detik |
| Rail 600 dp, font 200% | **1 tes lulus**, 22,035 detik; lima tujuan, action dari setiap tab, form ≤560 dp |
| Landscape 640 dp, font 200% | **2 tes lulus**, 29,133 detik; rail yang dapat digulir serta kalender sampai baris tanggal terakhir |
| Setelah wiring jaringan dan startup adaptif | **5 tes lulus** pada ponsel 200% (35,744 detik), **3 tes lulus** pada landscape 200% (36,640 detik) |
| Lima tab dan action manual | Kelima tab memiliki Role.Tab, target ≥48 dp; Catat membuka editor dan Back kembali ke asal, tanpa tab keenam |
| Stack, filter, rentang, rotasi | Detail kembali ke riwayat; filter kategori dan rentang 30 hari bertahan saat ganti tab serta Activity.recreate |
| Create/edit | 0,25 kg → 250 g; beralih benda mengosongkan berat; edit menjadi 2 benda tidak menambah aktivitas atau XP kedua |
| Dirty Back dan logout | App bar/system Back memakai D03; cancel mempertahankan isian; D02 cancel menjaga sesi, confirm membuang layar/data akun |
| Auth dan identitas | Validasi sebelum request, IME satu submit, loading disabled, error credential mempertahankan email, konfirmasi/password memakai spasi persis; identitas belum pasti menyembunyikan profil |
| Keyboard nyata | IME terbuka; password invalid tetap fokus; seluruh batas tombol Masuk berada di atas visible display frame, termasuk font 200% |
| Dataset dan tema | 650 g ditangani hari contoh, 2 benda dihindari terpisah, rate 1/2; periode 1,4 kg dan rate 2/4. Terang/gelap dan pilihan tema diperiksa |
| Kalender | Tanggal di luar batas nonaktif, Apply menerbitkan LocalDate, Cancel tidak menerapkan perubahan, dan tanggal di baris bawah dapat dipilih dengan usapan vertikal |
| Process death | Proses aplikasi dibunuh saat background; task yang sama dipulihkan, PID berubah dan field password kosong. Ini pengujian kematian proses, terpisah dari Activity.recreate |

Jumlah di atas adalah **40 eksekusi dari 22 kasus perangkat**, termasuk pengulangan pada konfigurasi berbeda dan verifikasi perubahan terakhir. Locale emulator en-US; teks aplikasi tetap resource Indonesia. Window 600 dp memakai density 288 dengan resolusi fisik tetap; landscape memakai rotasi pada density 480. Emulasi ukuran tidak diklaim sebagai uji perangkat tablet/foldable fisik.

CI membangun APK instrumentasi, tetapi emulator dijalankan lokal. Status CI untuk setiap push dibaca pada PR agar laporan tidak mengklaim hasil untuk head berbeda. Layanan password manager pihak ketiga dan backend nyata belum diuji; ContentType autofill/paste tersedia pada client.

## Runtime debug dan aksesibilitas API 36

APK debug normal dengan HotSwan dijalankan pada AVD API 36 x86_64, 1080 × 2400, density 420 (411 dp). Lima kasus AppNavigationTest lulus: dataset/lima tab/tema, create/edit/logout, filter/rotasi, keluar dari Home dan validasi dengan keyboard nyata. Pada percobaan pertama keyboard virtual tidak muncul karena `show_ime_with_hard_keyboard=0`; setelah konfigurasi QA diatur ke 1, kasus keyboard lulus dalam 11,683 detik. Empat kasus lainnya sudah lulus pada suite awal. Ini melengkapi bukti native API 24, bukan menggantikannya.

**TalkBack 16.0.0.738667889, font 200%: satu tes lulus dalam 17,578 detik.** Tes memastikan layanan spoken feedback terikat dan touch exploration aktif, kemudian mempertahankan layanan melalui FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES. Keyboard USB virtual dibuat melalui perintah HID Android; tes menunggu InputDevice eksternal terdaftar sebelum mengirim report. Fokus aksesibilitas nyata dipantau saat shortcut next-item membawa pembaca ke email/password, lalu mengaktifkan Masuk, mencapai pesan password invalid, dan kembali ke aksi utama. Nilai input fixture diisi melalui ACTION_SET_TEXT; navigasi dan aktivasi memakai layanan TalkBack. Tidak mengklaim evaluasi kualitas suara atau seluruh gestur sentuh pembaca layar. Lihat [shortcut TalkBack](https://support.google.com/accessibility/android/answer/6110948?hl=en), [UiAutomation](https://developer.android.com/reference/android/app/UiAutomation#FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES), serta [protokol HID Android](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/cmds/hid/README.md).

**Gestur Back API 36, font 200%: satu tes lulus dalam 23,150 detik.** Dengan navigation_mode=2 dan TalkBack nonaktif, usapan dari tepi kiri memicu D03 pada draf yang diubah. Lanjut mengisi mempertahankan teks dan editor; tidak kembali diam-diam ke tab. Bukti ini memeriksa hasil commit gestur Back dan kebijakan draf, bukan kualitas animasi predictive Back pada setiap frame.

Ringkasan tambahan: **7 kasus/eksekusi sukses API 36** (5 runtime debug, 1 TalkBack, 1 gestur). Tes khusus pembaca/gestur memerlukan argumen eksplisit dan lingkungan yang sesuai; tidak dianggap lulus karena dilewati pada perangkat lain. Hasil ada di [platform-api36.json](../qa/iteration-1/platform-api36.json). Semua perubahan setting hanya pada emulator khusus QA dan dipulihkan setelah pengujian.

## Regresi yang ditemukan dan diperbaiki

1. Konten daftar sebelumnya diukur sampai belakang dock opak. Scroll-to menganggap baris terlihat, tetapi tap mengenai dock. NavDisplay sekarang menerima viewport Scaffold yang nyata, dan mengonsumsi padding satu kali; area app bar/dock tetap opak. Tes kemudian dapat memilih record/editor yang benar.
2. `windowSoftInputMode` semula dipasang pada `<application>` sehingga tidak mengatur Activity. Android memindahkan jendela saat keyboard muncul; sebagian tombol terpotong. Atribut dipindahkan ke MainActivity, auth memakai safeDrawing dari Scaffold, dan form editor memakai imePadding. [Dokumentasi Android](https://developer.android.com/guide/topics/manifest/activity-element#wsoft) menetapkan atribut ini pada activity.
3. Gate `verify_minimum_sdk.py` kini membaca manifest APK dan memastikan MainActivity memuat `adjustResize`. Gate menolak APK sebelum perbaikan dan menerima APK sesudahnya. Ini melengkapi pemeriksaan font/OFL, minimum SDK dan ketiadaan interpreter pada APK native.
4. Action rail semula memecah Catat per huruf pada font besar. Ikon kini berada di atas teks, dan rail bertambah dari 112 menjadi 144 dp pada font scale ≥1,5.
5. Error respons login untuk field nama dapat mencoba memfokuskan field yang tidak ada. Pemetaan error kini hanya menerima field pada form aktif; tes login menyertakan field nama tak terduga dan tetap menampilkan error credential tanpa crash.
6. Jalur offline repository semula belum menerima snapshot jaringan dari UI. RemoteAccountSession kini mendapat provider jaringan default aktif dari ConnectivityManager, dengan izin normal ACCESS_NETWORK_STATE. Snapshot dibaca ulang pada retry; ini petunjuk ketersediaan transport, bukan bukti API dapat dijangkau atau authorization. Endpoint tetap menentukan hasil request. Startup juga memakai safe drawing dan scroll agar kedua aksi dapat dijangkau pada jendela pendek.

Tes daftar memakai scroll container untuk mencari item LazyColumn yang belum dikomposisi. Pengujian password memeriksa teks yang dimask serta nilai raw pada gateway fixture secara terpisah. Perbaikan tes tersebut mempertahankan assertion perilaku; tidak mengubah produksi agar mengikuti ekspektasi tes yang salah.

Hari kalender Material 3 diekspos sebagai teks tanggal lengkap. Scroll-to pada hari berhenti pada LazyRow bulan horizontal; tes landscape sekarang mengusap viewport vertikal dan memeriksa tinggi target tanggal penuh ≥48 dp sebelum ketukan. Assertion tanggal terpilih dan hasil Apply tetap diperiksa.

## Verifikasi build dan unit

Hook commit repo menjalankan `lintDebug test --daemon -q` tanpa bypass. Pada checkpoint `d862cd7`, report debug berisi **99 tes, 0 gagal/error/skip**: termasuk 27 tes repository sesi, 9 tes SessionViewModel dan 3 tes lifetime WorkspaceViewModel. Mapper payload, parser/model, transport, aturan akun, dataset dan draf juga masuk suite. Build native app/test, gate SDK/font/OFL/manifest dan hasil process death diperiksa terpisah dari unit test.

Build debug/staging/release, APK instrumentasi dan lint staging/release juga lulus lokal. CI head `df7bf7a` lulus untuk [push](https://github.com/waste-not-app/waste-not-android/actions/runs/38669031416) dan [PR #95](https://github.com/waste-not-app/waste-not-android/actions/runs/38669087297); report PR yang diunduh berisi 99 tes, 0 gagal/skip, keberhasilan 100%. Hasil head berikutnya dicatat pada PR/issue setelah workflow selesai.

Review sebelum merge menambahkan regresi trim catatan dan timestamp edit demo: **101 unit test native lulus**. Dua tes API 24 tambahan lulus dalam 13,389 detik untuk presisi ringkasan handled/avoided dan alur create/edit/dirty Back/logout. Rincian koreksi dan batas review ada di [PR_REVIEW.md](PR_REVIEW.md).

## Gambar perangkat

Lima gambar API 24 berasal dari APK native dan memakai data contoh. Gambar TalkBack tambahan berasal dari debug normal API 36 dan memakai input fixture. Beberapa layar digulir untuk memeriksa angka atau aksi tertentu; gambar tidak diubah.

| Tampilan | Gambar |
|---|---|
| Ponsel terang | [Beranda](../qa/iteration-1/home-light.png) |
| Ponsel gelap | [Beranda](../qa/iteration-1/home-dark.png) |
| Error dan aksi di atas keyboard, 200% | [Masuk](../qa/iteration-1/keyboard-font200.png) |
| Editor bersama, 200% | [Edit](../qa/iteration-1/editor-font200.png) |
| Rail lebar 600 dp, 200% | [Lima tujuan](../qa/iteration-1/rail600-font200.png) |
| TalkBack aktif, font 200%, API 36/debug normal | [Fokus Masuk dan pesan validasi](../qa/iteration-1/talkback-font200.png) |

## Gate UI

- **Hard gate PASS untuk kontrol client yang diimplementasikan:** build/run native, tindakan form/tab/dialog, error/loading/empty state, label sumber contoh, target sentuh dan kontras diperiksa. Backend write/panduan/AI yang belum aktif diberi keterangan terlihat.
- **Purpose gate PASS:** pinus/lime, gradient ringkasan, Plus Jakarta Sans dan ikon tujuan mengikuti DESIGN.md; gradient menandai ringkasan ditangani, lime menandai tindakan/Reduce terpisah.
- **Liveliness PASS:** ENERGY 2 / RHYTHM 2 / MOTION 1; hierarki angka ringkasan dan tindakan Catat menjadi fokus, tanpa gerak dekoratif tambahan.
- **Craft gate PASS pada konfigurasi yang dicatat:** kedua tema, font besar, dock/rail, keyboard dan kalender dapat digunakan; data contoh memiliki sumber PRD. Batas layanan aksesibilitas dan platform yang belum diuji tetap dicatat di atas.

## Mengulang pengujian

Bangun APK app dan test native, kemudian pasang keduanya pada emulator QA:

```sh
./gradlew assembleDebug assembleDebugAndroidTest -Pwastenot.hotReload=false
python3 scripts/verify_minimum_sdk.py app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell settings put system font_scale 2.0
adb shell settings put secure show_ime_with_hard_keyboard 1
adb shell am instrument -w -r \
  -e class com.example.wastenot.ui.main.AppNavigationTest,com.example.wastenot.ui.main.SessionRootTest \
  com.example.wastenot.test/androidx.test.runner.AndroidJUnitRunner
```

Gunakan emulator tersendiri. Pulihkan font/density setelah QA; jangan mengubah perangkat pengguna. WideNavigationTest dijalankan khusus pada window ≥600 dp dan dilewati pada ponsel sempit.

Untuk TalkBackNavigationTest gunakan emulator API 36 tersendiri dengan layanan TalkBack aktif dan font_scale=2.0, lalu tambahkan `-e talkback true -e class com.example.wastenot.ui.main.TalkBackNavigationTest` pada perintah instrumentasi. APK debug normal dapat dipakai. Untuk gestur, nonaktifkan TalkBack, pastikan mode navigasi gestur, kemudian gunakan `-e predictiveGesture true -e class 'com.example.wastenot.ui.main.AppNavigationTest#edgeBackGestureUsesDirtyDraftPolicy'`. Tes HID melepas keyboard virtual dalam finally; tidak membutuhkan akun Google.
