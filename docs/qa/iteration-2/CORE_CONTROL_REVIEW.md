# Progress XP dan pilihan tema sesuai kit

Checkpoint 8 November 2026, dasar source `561e369`. Review pasangan viewport awal S04/S05/S07/S08/S11/S12/S13/S14/S16 dalam dua tema menemukan dua selisih kontrol. Referensi kit masih memakai `design-system.html` SHA `ef0db8a952c2a947ac00726c58ebd05167fdecbbbd508d507afbdeb721f401af`; file kit tidak diedit. Referensi memiliki kanvas 360 px, sedangkan capture API 37 memiliki lebar 390 dp; perbedaan jumlah konten/tanggal/data dan capability tidak diperlakukan sebagai kegagalan styling.

## Perubahan dan hasil terukur

Progress XP pada Beranda/Profil mengikuti CSS `progress`: tinggi 8 dp, track divider kontinu, fill primary bersudut bulat. Gap Material bawaan dihilangkan dan latar track menjaga area pertemuan kedua ujung tetap terisi. Nilai XP dan semantics progres tetap memakai komponen yang sama.

Pilihan tema S16 memakai token outline kontrol, minimum 56 dp, nilai 16/24 sp, ikon 24 dp dan radius 12 dp. Label kelompok serta padding mengikuti field kit. Callback pemilih tema dipertahankan.

| Ukuran pada density 3 | Sebelum | Sesudah |
|---|---|---|
| Tinggi cat progress | 21 px / 7 dp | 24 px / 8 dp |
| Piksel latar yang memutus tengah track | 11, kedua tema | 0, kedua tema |
| Tinggi pilihan tema | 156 px / 52 dp | 168 px / 56 dp |
| Border tema terang terhadap surface | 1,32:1 | 3,97:1 |
| Border tema gelap terhadap surface | 1,65:1 | 4,74:1 |

[Metrik raster](core-control-review/raster-metrics.json) berasal dari PNG asli; warna border diambil pada sisi vertikal kontrol. Kontras tersebut terhadap surface kontrol, bukan klaim seluruh pasangan warna aplikasi. Pemeriksaan teks memakai `TextLayoutResult` native dan memastikan nilai tema 16 sp.

## Bukti sebelum dan sesudah

| Kontrol | Sebelum | Sesudah |
|---|---|---|
| XP terang | [PNG](core-control-review/baseline/xp-light.png) | [PNG](core-control-review/after/xp-light.png) |
| XP gelap | [PNG](core-control-review/baseline/xp-dark.png) | [PNG](core-control-review/after/xp-dark.png) |
| Pilihan tema terang | [PNG](core-control-review/baseline/theme-light.png) | [PNG](core-control-review/after/theme-light.png) |
| Pilihan tema gelap | [PNG](core-control-review/baseline/theme-dark.png) | [PNG](core-control-review/after/theme-dark.png) |

Dua metode native dengan **APK penguji identik** SHA `d3e45a4c39732d6099259e066f2a9ce1367674f7f7c505b2d1cccac1c67732d9` mereproduksi dua kegagalan desain pada [baseline 12,697 detik](core-control-review/baseline/tests.log), kemudian [lulus dua tes 13,222 detik](core-control-review/after/tests.log). Callback tema terjadi sekali per ketukan pada masing-masing tema. APK aplikasi terpasang diverifikasi checksum-nya: baseline `8a35548c5dfb3b59c00c1af88c8fd1b5c9df3e597db67aec7da1ef1035680413`, sesudah `eb3688bb344d5a88134b10340ddcd6eef67c1e0256e8bf62b86ff31f77d355e9`. [Hash source/build](core-control-review/source-build.json), [runtime sebelum](core-control-review/baseline/result.json), [runtime sesudah](core-control-review/after/result.json), [16 gambar](core-control-review/capture-manifest.json).

Ini fixture komponen pada API 37, 390×844 dp dan font normal, bukan capture ulang seluruh Beranda/Profil maupun penerimaan semua state/font. Review Standards: 0 temuan; Spec: 0 temuan. Pengujian commit/CI dan APK preview dicatat terpisah. Perbaikan driver source intent serta [tiga tes panduan AI](POLICY08_ANDROID.md) juga sudah memiliki bukti native, tanpa mengubah produksi untuk menyesuaikan tes.
