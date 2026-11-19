# Pergantian hari akun dan penjelasan streak

9 November 2026, AT-22/A36/#70 dan S14/A28/#55. Audit menemukan dua celah: tanggal akun hanya dibaca saat composition, sehingga aplikasi yang tetap terbuka melewati tengah malam tidak memuat ringkasan baru; Profil juga belum memakai `hasLoggedToday` untuk menjelaskan streak yang bertahan dari kemarin. Acuan: IMPLEMENTATION_PLAN AT-22 dan PRD §10.

## Perubahan

Pengamat tanggal pada AccountNavigation mengikuti zona akun dan lifecycle STARTED. Pengamat menjadwalkan batas hari berikutnya, memeriksa koreksi jam paling lambat setiap satu menit, serta membaca ulang tanggal ketika Activity aktif kembali. Tanggal yang sama tidak diterbitkan berulang; ini bukan polling HTTP setiap menit. Referensi tanggal fixture yang sudah ditentukan tetap dipertahankan. Jam perangkat dan server tidak diubah.

Perubahan tanggal memanggil refresh dashboard dan periode Statistik yang sudah dipilih. Pilihan 7/30 hari tetap tersimpan. Draf yang sedang terbuka mempertahankan tanggal, jumlah dan catatannya; draf baru memakai tanggal baru. Profil menampilkan “Belum mencatat hari ini.” hanya ketika server memberi streak saat ini positif dan `hasLoggedToday=false`. Streak nol atau belum diketahui tidak ditebak sebagai streak kemarin.

## Baseline, unit dan native

APK sebelum perbaikan berasal dari main `8d3859c` dengan Clock yang dapat diinjeksi untuk pengujian; default sistem tidak berubah. Dua assertion baseline gagal tepat pada pembaruan hari tanpa navigasi dan ketiadaan penjelasan streak. [Log tanggal](account-day/before/clock.log), [log streak](account-day/before/streak.log), [hasil](account-day/before/native-result.json), [binding APK/sumber](account-day/before/build-manifest.json).

**274 debug unit tests lulus, nol failure/error/skip**, termasuk tiga tes baru untuk tengah malam Asia/Jakarta sebelum UTC berganti hari, hari 23 jam saat DST New York dan koreksi jam maju/mundur tanpa emisi tanggal duplikat. [Hasil](account-day/unit-result.json), [lint/unit/build](account-day/final-build.log).

**Enam eksekusi native akhir lulus**, tiga metode pada masing-masing tema, API37/390×844 dp/font100%:

- Statistik 30 hari bergerak dari 11 Okt–9 Nov menjadi 12 Okt–10 Nov tanpa tap navigasi. Beranda menjadi 10 November dengan total harian kosong; Profil tetap menerima streak dua hari dengan penjelasan belum mencatat hari ini.
- Setelah Activity berhenti lalu aktif kembali pada 12 November, rentang menjadi 14 Okt–12 Nov. Resume pada hari yang sama tidak menambah request dashboard/statistik.
- Draf 250 g dengan catatan dan tanggal 9 November tetap sama setelah pergantian hari. Sesudah dibuang, draf baru bertanggal 10 November dan kosong. Streak yang sudah mencatat hari ini, nol atau belum diketahui tidak menampilkan pesan kemarin.

[Terang](account-day/after/light.log), [gelap](account-day/after/dark.log), [hasil akhir](account-day/after/native-result.json). Tes memakai Clock terkontrol dan respons berbentuk kontrak server pada source pengujian; tidak ada akun backend atau panggilan AI baru. Ini membuktikan pengamat tanggal, request/rentang dan UI, bukan pengujian ulang perhitungan backend pada tengah malam nyata. Dataset backend lima baris AT-10 tetap terpisah.

## Gambar yang diterima

Dua puluh PNG asli ditinjau: **19 diterima**, satu frame kosong dari fixture Profil terang dikeluarkan dari bukti visual. Frame kosong tetap disimpan; keadaan yang sama terbukti melalui gambar Profil dari perjalanan navigasi. [Hasil review](account-day/capture-review.json). Window ComponentActivity memakai status bar default; warna ikon status bar fixture bukan penerimaan window MainActivity. [Perjalanan MainActivity nyata](AI_MAIN_REGRESSION.md) dan startup staging di bawah mempunyai scope window terpisah.

| Keadaan | Terang | Gelap |
|---|---|---|
| Rentang setelah tengah malam | [PNG](account-day/after/captures/statistics-at-day-boundary-light.png) | [PNG](account-day/after/captures/statistics-at-day-boundary-dark.png) |
| Rentang setelah kembali dari latar belakang | [PNG](account-day/after/captures/statistics-after-resume-light.png) | [PNG](account-day/after/captures/statistics-after-resume-dark.png) |
| Beranda pada hari baru | [PNG](account-day/after/captures/home-after-midnight-light.png) | [PNG](account-day/after/captures/home-after-midnight-dark.png) |
| Streak kemarin, belum mencatat hari ini | [PNG](account-day/after/captures/profile-yesterday-streak-light.png) | [PNG](account-day/after/captures/profile-yesterday-streak-dark.png) |
| Draf lama dipertahankan | [PNG](account-day/after/captures/draft-preserved-after-midnight-light.png) | [PNG](account-day/after/captures/draft-preserved-after-midnight-dark.png) |
| Draf baru memakai hari baru | [PNG](account-day/after/captures/new-draft-current-day-light.png) | [PNG](account-day/after/captures/new-draft-current-day-dark.png) |

Pembanding: [rentang masih 9 November](account-day/before/captures/statistics-at-day-boundary-light.png), [streak tanpa penjelasan](account-day/before/captures/streak-today-pending-light.png). Font, warna dan komponen kit tetap dipakai; tambahan Profil menggunakan teks pendukung yang sama dengan basis streak, tanpa kartu atau animasi baru.

## APK dan pemulihan pengujian

| Paket | SHA-256 |
|---|---|
| Debug native | `91f92558709a3b4c04fbad6889d0df92dd20da78b271e57fdff6399009ffb82e` |
| Staging native | `f65e9f9a6fddc18bc4635a1d52a5d79306e7e234942514620d5e5fd5eb3113ff` |
| Penguji untuk baseline dan hasil akhir yang diterima | `815ed1b40d0c59e0ada917e1e6248819dc50b14524b0554d49288056da1e7acf` |

[Manifest akhir](account-day/after/build-manifest.json). SDK minimum API30, adjustResize, lima font/OFL, adapter remote dan signature staging v2 lolos gate. Staging belum terpasang pada emulator sesi ini, sehingga APK pendahulu `7b3c41ef` dipasang, dipilih Gelap dan direstart untuk memastikan pilihan tersimpan. Upgrade ke APK baru tanpa clear data membuka [form masuk](account-day/staging-startup.png) dan tetap memilih Gelap; [hasil](account-day/staging-result.json). [Pointer APK lokal](account-day/delivery.json) diperbarui dengan pendahulu tetap diarsipkan.

Sebelum instrumentation, dialog ANR System UI dipulihkan dan launcher diperiksa. Fixture awal sengaja membiarkan streak null, sehingga batch terang pertama melewati pemeriksaan tanggal/draf tetapi gagal saat meminta pesan streak positif. Fixture diperbaiki dengan nilai streak terkontrol; kedua assertion baseline diulang dengan APK penguji yang sama dengan enam eksekusi akhir. [Penjelasan](account-day/fixture-correction.md), [hasil awal](account-day/initial/final/native-result.json), [log awal](account-day/initial/final/light.log). APK produk tidak berubah selama koreksi fixture. Artefak awal lengkap tetap tersimpan di direktori QA lokal. Ukuran/density/font/night dipulihkan dan emulator task dihentikan; preferensi staging sengaja tetap Gelap sebagai hasil upgrade.

Self-review memeriksa kepemilikan coroutine/lifecycle, batas zona/DST, invalidasi periode, preservasi draf, flag server dan sumber APK. Tidak ditemukan penghalang pada scope perbaikan. Gate Anti Slop selama implementasi lulus untuk tujuan/fungsi dan 19 frame yang diterima; kit ENERGY2/RHYTHM2/MOTION1 dipertahankan. Ini checkpoint AT-22 Android pada konfigurasi tersebut, bukan penerimaan seluruh aplikasi, seluruh jaringan/perangkat, font besar atau TalkBack. Status CI/merge dirujuk dari PR; goal penuh tetap aktif.
