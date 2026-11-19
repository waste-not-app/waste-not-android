# Audit UI Android: liquid glass

Tanggal: 16 November 2026. Target: Android 1.0.5 (versionCode 4).
SMTP ditunda atas permintaan pemilik; audit ini tidak mengubah layanan email/backend.

## Arah dan alasan

- Identitas tetap artwork W, pinus, krem, lime dan Plus Jakarta Sans dari design kit. ENERGY 2 / RHYTHM 2 / MOTION 1.
- Glass hanya pada dock dan header: keduanya menjelaskan navigasi yang berada di atas konten. Haze mengambil backdrop sebenarnya, bukan memburamkan teks/ikon. Rim gradien dan shadow membedakan lapisan; ini interpretasi Android, bukan material native Apple atau simulasi refraksi.
- Isi, form, status, peringatan dan dialog memakai surface opak agar angka dan pilihan tidak bercampur dengan backdrop. Fallback Haze juga memakai surface opak.
- Dock mempertahankan satu baris dengan label lengkap. Jika teks besar tidak muat, baris dapat digulir horizontal dan pilihan aktif dibawa ke viewport. Ini mengganti dock bertingkat yang menghabiskan ruang baca.
- Header bertambah tinggi mengikuti teks, bukan memotong judul dengan tinggi app bar tetap. Konten tetap memiliki ruang akhir sesuai ukuran chrome aktual dan inset sistem.
- Brush ambient disimpan dengan `drawWithCache`, bukan dibentuk ulang setiap frame. Tidak ada animasi dekoratif berulang atau dependency baru.

## Temuan dan perbaikan

| Area | Masalah yang ditemukan pada sumber | Perbaikan |
|---|---|---|
| Kontrol bersama | Label aksi memakai lebar intrinsik di Row; label panjang dapat melampaui ruang | Label mengikuti batas ruang dan membungkus; focus outline dipertahankan |
| Field/secondary button | Edge field transparan hanya sekitar 1,27:1 terang dan 1,61:1 gelap | Edge solid; outline terang diperkuat, termasuk border secondary button |
| Shell | Header tinggi tetap; dock menjadi beberapa baris saat font besar | Header tumbuh; dock satu baris scrollable, target minimal 48 dp, pilihan lime dengan foreground khusus |
| Ringkasan | Judul/info dan level/XP bersaing dalam Row | Judul berbobot, info 48 dp, level/XP dapat pindah baris |
| Auth/akun | Heading dan form landscape sempit; banner appearance mengonsumsi inset hanya pada subtree sendiri | Wrapping/stacking; konsumsi status inset pada parent bersama |
| Onboarding | Footer tetap mengurangi ruang pager; indikator 8 dp; angka ilustrasi tidak diberi label contoh | Footer ikut scroll, tab 48 dp, ilustrasi berlabel Contoh tampilan |
| Profil/pengaturan | Nama/inisial/metadata dapat terdesak; pemisah menu tidak konsisten | Avatar tumbuh, metadata membungkus, pembacaan dekorasi tidak diduplikasi |
| Pengingat/tema | Toggle terbatas pada switch; picker dapat melampaui ruang | Seluruh label dapat men-toggle; pilihan/dialog dapat scroll dan input waktu ringkas |
| Tanggal | Render aktual 320 dp/font 200% memotong kolom tepi kalender native | Mode input tanggal pada ruang sempit/font besar, kalender tetap tersedia pada ruang yang cukup; batas tanggal tetap divalidasi |
| Riwayat/panduan | Banner/header filter/disclosure bersaing dalam Row; fokus baris panduan lemah | Penyusunan vertikal, wrapping, role/focus dan pengumuman status |
| Statistik/pencapaian/detail | Kolom tetap serta label/nilai/timezone panjang bersaing | Kelompok informasi mengalir ulang dan metadata bertumpuk |
| Sinkronisasi | Surface transparan dan kerja aktif tidak jelas; timeout tidak memperhitungkan aksesibilitas | Surface opak, target 48 dp, status kerja tertulis, timeout aksesibel dan jeda saat fokus |
| Scan | Header/footer tetap menyisakan terlalu sedikit ruang pada font besar | Seluruh isi terikat area scroll, termasuk heading dan fallback manual; state/lifecycle kamera tetap |
| Dialog/recovery | Fokus dapat diminta sebelum window siap; error tidak diumumkan | Fokus menunggu window siap dan kontrol aktif; Escape/busy guard dan live status |

Cakupan sumber: startup, onboarding, login/register, pemulihan/hapus akun, beranda,
riwayat/filter, editor, konfirmasi/detail/hapus catatan, draf/konflik/keluar,
panduan daftar/detail, statistik, pencapaian, profil/pengaturan, pengingat/tema,
lokasi daftar/detail, sinkronisasi, serta semua fase scan. Titik pengumpulan,
konfirmasi simpan, kamera dan dialog keluar yang sudah memenuhi struktur tidak
mendapat perubahan kosmetik tanpa alasan.

## Verifikasi host

- Build debug, lint debug dan kompilasi source instrumentation lulus. Tidak ada pengujian instrumentation yang dijalankan di perangkat.
- Launcher visual sementara merender 129 state/surface pada 390×840/font 100%, 320×640/font 200%, dan 600×900/font 200%, masing-masing terang/gelap. Tercatat 1.002 PNG, termasuk window dialog terpisah. Rail 600 dp juga dirender dengan konfigurasi lebar sebenarnya.
- Font scale diatur pada runtime Android pengujian agar window dialog turut membesar, bukan hanya `LocalDensity` layar induk. Kalender yang terpotong ditemukan dari render ini; mode input penggantinya diperiksa ulang.
- Inspeksi visual memakai contact sheet layar utama/akun/konten/scan/dialog kedua tema serta sampel font besar, kalender, input waktu, metadata panjang dan rail. Ini bukan klaim inspeksi manual satu per satu terhadap seluruh PNG.
- Lima regresi UI host memeriksa wrapping label dan aktivasi Enter, lima tujuan dock/ruang baca/aksi scan, header dan Back, simpan editor dengan jumlah 250 g, serta input tanggal di luar rentang dan tanggal valid. Dua kontrak kontras dan dua regresi pemulihan izin pengingat juga lulus.
- Screenshot memakai native graphics Robolectric API 35 dan `View.draw(Canvas)`; PixelCopy host timeout sehingga tidak dipakai sebagai bukti. Render software tidak membuktikan backdrop blur/refraction GPU pada HP.
- Launcher dan fixture gallery sementara telah dihapus. Hanya regresi consumer-visible yang dipertahankan; tidak ada fixture dimasukkan ke source aplikasi.

Bukti terpilih: [beranda terang](liquid-glass/home-light.png),
[beranda gelap](liquid-glass/home-dark.png),
[kalender sebelum perbaikan](liquid-glass/date-picker-before-font200.png), dan
[input tanggal sesudah perbaikan](liquid-glass/date-picker-after-font200.png).

## Batas bukti

Audit sumber tidak sama dengan uji perjalanan di perangkat. Capture gallery hanya
menjalankan state awal dengan callback fixture; bukan seluruh navigasi, validasi
dan aksi di bawah fold. Slot kamera menggunakan teks uji, tanpa preview foto atau
bounding box. Bukti interaksi spesifik dibatasi pada regresi yang disebut di atas.

Tidak ada emulator atau ponsel yang diakses. Kamera nyata/Photo Picker, keyboard
sistem, TalkBack, insets gesture/tiga-tombol, serta performa blur GPU pada API 30/31+
belum dinyatakan lulus. Pemeriksaan itu tetap memerlukan ponsel fisik dan permintaan
pemilik. Screenshot host tidak membuktikan perilaku GPU atau pengiriman email.
