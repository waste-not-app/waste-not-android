# A06: Tema dan font WasteNot

Issue [#7](https://github.com/waste-not-app/waste-not-android/issues/7), branch `codex/android-theme-forms`, In progress.

## Arah visual

Mengikuti DESIGN.md v1.3 dan screenshot preview-glass.png: pencatatan pribadi untuk pengguna muda, utilitarian yang ramah, ENERGY 2 / RHYTHM 2 / MOTION 1. Pinus membedakan tindakan utama; lime menandai progres. Satu ringkasan utama menjadi fokus, kemudian daftar. Typography memakai Plus Jakarta Sans agar label/form dan angka memiliki hierarki konsisten. Gradien ringkasan menjaga fokus angka, gradien aksi mengikuti tema, canvas memberi bidang latar yang tenang. Surface kontrol/dock tetap opak sebagai baseline.

Anti Slop diterapkan selama implementasi sesuai pilihan pengguna. Dummy untuk backend/AI diizinkan; layar harus menandai data contoh dan menyediakan sumber data yang mudah diganti ke API. Dummy bukan bukti inference/backend nyata.

## Perubahan

- Lima font statis weight 400/500/600/700/800 dibuat dari variable font dalam kit dengan fontTools 4.66.1. Metadata OS/2 cocok dengan setiap weight dan tabel fvar tidak tersisa, sehingga tidak bergantung dukungan variable font perangkat. Lisensi sumber disertakan di assets/licenses/OFL-PlusJakartaSans.txt.
- Semua 15 role Typography ditetapkan sesuai ukuran/line height DESIGN, dengan FontSynthesis.None dan tabular numerals.
- WasteNotTheme memetakan seluruh peran ColorScheme Material 3, termasuk error gelap, on-colors, surface containers dan fixed colors. Dynamic color tetap tidak digunakan. Parameter darkTheme mendukung pilihan eksplisit atau sistem sebagai default.
- Spacing, shapes 8/12/16/24 dp, dan gradien ringkasan/lime/aksi/canvas dipusatkan dalam theme. Beranda awal memakai gradien theme dan token teks sekunder ringkasan.
- Kontrol Tampilan memilih Sistem/Terang/Gelap dan menerapkannya segera. Pilihan disimpan lokal dengan SharedPreferences untuk satu nilai nonrahasia; tidak menambah dependency. Ikon/scrim system bar mengikuti pilihan eksplisit, termasuk fallback navigation bar pada API lama. Kontrol sementara berada pada root beranda awal dan akan dipindah ke pengaturan saat A09 terhubung.

## Minimum SDK dan CI

Pemasangan APK debug pertama pada API 24 ditolak dengan INSTALL_FAILED_OLDER_SDK: manifest debug meminta 26 meskipun defaultConfig menetapkan 24. Staging/release tetap 24. Bytecode plugin HotSwan 2.0.1 menunjukkan floorMinSdkForInstrumentedVariants menaikkan varian debug; opsi enabled pada dokumentasi web versi lain tidak tersedia pada extension 2.0.1.

Build sekarang menyediakan `-Pwastenot.hotReload=false` yang melewati penerapan plugin development tersebut secara utuh untuk pemeriksaan native. Default tetap true untuk alur hot reload. DTO credential tetap memiliki pengecualian rewrite sempit yang sudah ada. APK native terpasang dan suite perangkat lulus pada emulator API 24.

CI menambahkan build native app/test APK dan gate distribusi font/lisensi/minimum SDK, lalu membangun kembali debug HotSwan, staging, release seperti sebelumnya. Native APK tersimpan sebagai artifact terpisah. CI belum menjalankan emulator; pengujian perangkat lokal dilaporkan terpisah. `verify_minimum_sdk.py` juga memastikan asset interpreter tidak masuk APK native.

## Verifikasi dan pekerjaan tersisa

Build native app/test APK lulus. Gate minimum SDK/font/OFL lulus pada APK terkemas. Sebelas tes perangkat lulus pada API 24 dalam 13,616 detik: tiga tema, tiga interaksi form A07, empat Keystore dan satu Application/container. Tes tema memuat kelima font melalui ResourcesCompat, membaca lisensi paket, memeriksa kontras semua pasangan teks utama kedua tema dan 101 titik per gradien opak, serta memilih tiga mode pada font 200% dengan pembacaan ulang preferensi. Metadata OS/2 lima font statis telah diperiksa.

Tes password A07 memeriksa teks layout yang benar-benar dirender; semantics password dapat tetap ada saat teks terlihat. ColorUtils.blendARGB pada API 24 dapat membulatkan alpha 255 menjadi 254; pemeriksaan gradien menetapkan alpha opak sesuai token asli sebelum menghitung rasio.

- [x] Kontras pasangan teks utama, outline, dan gradien pada kedua tema.
- [x] APK memuat lima font dan lisensi; native font loading pada minimum API 24.
- [ ] Preview/perangkat untuk tema terang/gelap dan font 200%, tanpa clipping.
- [x] Pilihan sistem/terang/gelap pada root aplikasi dan kontrol tema tersimpan lokal.
- [ ] Lint, unit test, build dan CI untuk perubahan final.

Beranda masih starter; navigasi lima tab, forms/auth, serta sumber data dummy dikerjakan pada task current iteration berikutnya. Tes pemilih tema 200% belum membuktikan seluruh layar pada font 200% atau pembacaan TalkBack. Catatan ini belum menyatakan UI final selesai.
