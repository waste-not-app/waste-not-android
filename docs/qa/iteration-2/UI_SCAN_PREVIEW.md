# Preview UI: seluruh screen wajib dan dialog

Checkpoint implementasi mengikuti prioritas UI dahulu pada 6 November 2026. Struktur layar inti/AI dan dialog tercatat pada [rencana UI](../../UI_FIRST_PLAN.md). Snapshot kini mencakup **20/20 ID screen wajib dan 4/4 dialog**. Ini cakupan tampilan awal, bukan persentase kesiapan rilis atau penerimaan seluruh state.

## Perubahan

- Header scan dan indikator tahap mengikuti susunan kit; indikator tetap terlihat pada ponsel normal. Ruang vertikal sempit/font besar memakai indikator dalam area scroll agar isi tetap dapat dijangkau.
- Konfirmasi objek memakai pilihan material dan jenis benda dari katalog. Band deteksi server dipertahankan, termasuk kandidat yang belum pasti; material tidak diisi sebagai fakta dari skor.
- Kondisi, rencana penggunaan ulang dan penerimaan pengelola tetap independen. Pertanyaan tambahan muncul untuk kondisi kotor, HDPE, risiko, jawaban yang telah terisi atau konteks yang diminta server. UNKNOWN tetap pilihan yang sah.
- Kartu saran memakai material ring/gradient/border yang sama dengan ringkasan kit. Peringatan memakai warna warning; disclosure dasar saran mendahului konfirmasi metode. Teks diringkas agar CTA “Gunakan pilihan, isi jumlah” terbaca pada viewport normal.
- Saran bersyarat, kebutuhan konteks, prototipe, sumber dan penanganan khusus memiliki tampilan berbeda. Pilihan pengguna diteruskan ke editor dengan jumlah kosong; referensi live dipertahankan saat handoff tanpa menghapus deteksi sebelum core save.

## APK dan batas bukti

APK preview memakai **mode demo**, `hotReload=false`, minSdk 30. SHA-256 `48a94d4e70121bf744dd33e9005f0bb43798b74ef6319f92e9d17960fa233540`. Build APK aplikasi dan instrumentasi lulus. Pengambilan gambar memakai ponsel API 37, 390×844 dp, teks normal. Foto adalah virtual scene emulator; deteksi/saran pada gambar adalah contoh demo, bukan inference produksi.

Dua perjalanan UI pada APK final lulus: kamera → foto → material/jenis → kondisi → saran → editor, serta sembilan viewport inti dalam tema terang/gelap. Dua pengambilan tambahan juga lulus: fixture sesi/auth/fallback/konflik dan rute filter → detail → edit → buang perubahan → lanjut edit → simpan → profil/keluar. Total **67 PNG**: 31 capture inti/scan dan 36 capture tambahan. [Manifest hash, ukuran dan scope](ui-preview/capture-manifest.json), [APK/source binding](ui-preview/frozen-build.json), [log scan](ui-preview/scan-visual-journey.log), [log viewport](ui-preview/core-visual-journey.log), [log fixture](ui-preview/remaining/states.log), [log rute tambahan](ui-preview/remaining/routes.log), [test APK/source tambahan](ui-preview/remaining/test-build.json).

Capture awal yang mendahului frame terbaru ditolak dan disimpan terpisah pada artefak lokal, tidak masuk galeri ini. Pengambilan gambar scan kini menunggu capture Compose serta memeriksa window aplikasi sebelum screenshot display.

## Galeri dan acuan

| Layar | Native terang | Native gelap | Acuan kit |
|---|---|---|---|
| S04 Beranda | [PNG](ui-preview/native-core/S04-light.png) | [PNG](ui-preview/native-core/S04-dark.png) | [Terang](screen-review/kit-S04-light.png) · [Gelap](screen-review/kit-S04-dark.png) |
| S05 Catat | [PNG](ui-preview/native-core/S05-light.png) | [PNG](ui-preview/native-core/S05-dark.png) | [Terang](screen-review/kit-S05-light.png) · [Gelap](screen-review/kit-S05-dark.png) |
| S07 Riwayat | [PNG](ui-preview/native-core/S07-light.png) | [PNG](ui-preview/native-core/S07-dark.png) | [Terang](screen-review/kit-S07-light.png) · [Gelap](screen-review/kit-S07-dark.png) |
| S08 Detail | [PNG](ui-preview/native-core/S08-light.png) | [PNG](ui-preview/native-core/S08-dark.png) | [Terang](screen-review/kit-S08-light.png) · [Gelap](screen-review/kit-S08-dark.png) |
| S11 Panduan | [PNG](ui-preview/native-core/S11-light.png) | [PNG](ui-preview/native-core/S11-dark.png) | [Terang](screen-review/kit-S11-light.png) · [Gelap](screen-review/kit-S11-dark.png) |
| S12 Artikel | [PNG](ui-preview/native-core/S12-light.png) | [PNG](ui-preview/native-core/S12-dark.png) | [Terang](screen-review/kit-S12-light.png) · [Gelap](screen-review/kit-S12-dark.png) |
| S13 Statistik | [PNG](ui-preview/native-core/S13-light.png) | [PNG](ui-preview/native-core/S13-dark.png) | [Terang](screen-review/kit-S13-light.png) · [Gelap](screen-review/kit-S13-dark.png) |
| S14 Profil | [PNG](ui-preview/native-core/S14-light.png) | [PNG](ui-preview/native-core/S14-dark.png) | [Terang](screen-review/kit-S14-light.png) · [Gelap](screen-review/kit-S14-dark.png) |
| S16 Pengaturan | [PNG](ui-preview/native-core/S16-light.png) | [PNG](ui-preview/native-core/S16-dark.png) | [Terang](screen-review/kit-S16-light.png) · [Gelap](screen-review/kit-S16-dark.png) |
| S21 Kamera/foto | [Kamera](ui-preview/native-scan/camera-before-permission-panel.png) · [Foto](ui-preview/native-scan/camera-photo-review-panel.png) | Belum diambil pada checkpoint ini | [Kamera terang](scan-reference-camera-light.png) |
| S22 Objek | [PNG](ui-preview/native-scan/camera-object-review-panel.png) | Belum diambil pada checkpoint ini | SCREEN_SPEC S22, katalog material dan satu objek |
| S23 Kondisi | [PNG](ui-preview/native-scan/camera-context-panel.png) | Belum diambil pada checkpoint ini | SCREEN_SPEC S23; [contoh kit gelap](scan-reference-context-dark.png) bukan pasangan tema yang sama |
| S24 Saran | [PNG](ui-preview/native-scan/camera-recommendation-panel.png) | Belum diambil pada checkpoint ini | [Kit terang](scan-reference-recommendation-light.png) |

## Screen dan dialog tambahan

| ID | Terang | Gelap | Jenis bukti |
|---|---|---|---|
| S01 Pemulihan sesi | [Loading](ui-preview/remaining/S01-light.png) · [Gagal](ui-preview/remaining/S01-error-light.png) | [Loading](ui-preview/remaining/S01-dark.png) · [Gagal](ui-preview/remaining/S01-error-dark.png) | Fixture komponen |
| S02 Masuk | [PNG](ui-preview/remaining/S02-light.png) | [PNG](ui-preview/remaining/S02-dark.png) | Fixture komponen |
| S03 Daftar | [Awal](ui-preview/remaining/S03-light.png) · [Bawah](ui-preview/remaining/S03-end-light.png) | [Awal](ui-preview/remaining/S03-dark.png) · [Bawah](ui-preview/remaining/S03-end-dark.png) | Fixture komponen |
| S06 Tersimpan | [PNG](ui-preview/remaining/S06-light.png) | [PNG](ui-preview/remaining/S06-dark.png) | Setelah edit melalui navigasi demo; tanpa XP baru |
| S09 Ubah | [Awal](ui-preview/remaining/S09-light.png) · [Bawah](ui-preview/remaining/S09-end-light.png) | [Awal](ui-preview/remaining/S09-dark.png) · [Bawah](ui-preview/remaining/S09-end-dark.png) | Navigasi demo |
| S10 Filter | [Awal](ui-preview/remaining/S10-light.png) · [Bawah](ui-preview/remaining/S10-end-light.png) | [Awal](ui-preview/remaining/S10-dark.png) · [Bawah](ui-preview/remaining/S10-end-dark.png) | Navigasi demo |
| S25 Fallback | [PNG](ui-preview/remaining/S25-light.png) | [PNG](ui-preview/remaining/S25-dark.png) | Fixture gagal deteksi |
| D01 Hapus | [PNG](ui-preview/remaining/D01-light.png) | [PNG](ui-preview/remaining/D01-dark.png) | Dibuka dari detail; tidak menghapus |
| D02 Keluar | [PNG](ui-preview/remaining/D02-light.png) | [PNG](ui-preview/remaining/D02-dark.png) | Dibuka dari profil; dibatalkan |
| D03 Buang perubahan | [PNG](ui-preview/remaining/D03-light.png) | [PNG](ui-preview/remaining/D03-dark.png) | Dibuka dari editor; lanjut edit |
| D04 Konflik | [Awal](ui-preview/remaining/D04-light.png) · [Bandingkan](ui-preview/remaining/D04-compare-light.png) | [Awal](ui-preview/remaining/D04-dark.png) · [Bandingkan](ui-preview/remaining/D04-compare-dark.png) | Fixture versi; pilihan tidak mengirim ke server |

## Berikutnya

Inventaris dan review tampilan awal lengkap. Lanjutkan pengujian fungsi/state dan integrasi backend/AI nyata, termasuk unknown-write/handoff dan pergantian akun. Periksa bagian scroll yang belum direkam, tema gelap S21–S24, lintas perangkat/font, aksesibilitas dan penerimaan visual luas. Keberhasilan perjalanan demo dan fixture tidak menutupnya. Hook commit/CI tetap diperiksa terpisah; tidak ada klaim bahwa unit suite terbaru telah lulus hanya dari build/render ini.
