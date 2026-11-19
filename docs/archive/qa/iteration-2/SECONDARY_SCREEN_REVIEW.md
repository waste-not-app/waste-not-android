# Review layar tambahan dan dialog, 8 November 2026

Review visual menutup pembacaan **36 PNG** yang sudah tersedia untuk S01, S02, S03, S06, S09, S10, S25 dan D01–D04, pada dua tema. Tidak ditemukan koreksi produk baru pada frame yang ditinjau. Ini hasil review dalam cakupan di bawah, bukan penerimaan seluruh state, konfigurasi atau rilis.

Acuan tetap `SCREEN_SPEC.md`, `DESIGN.md`, HTML dan CSS kit v1.3. Lebar pratinjau web dan native berbeda; keputusan Android mengikuti skala tipografi/input dan perilaku layar pada spesifikasi, bukan menyalin setiap piksel browser. Data contoh, tanggal, hasil transaksi dan capability mengikuti sumber masing-masing.

## Hasil per layar

| ID | Bagian yang diperiksa | Hasil dan batas |
|---|---|---|
| S01 | Loading, gagal validasi sesi; wordmark, simbol, teks, progress/retry | Susunan dan dua tema sesuai kit. Teks “Menyiapkan catatanmu…” mengikuti SCREEN_SPEC. Tombol simulasi HTML tidak dibawa ke aplikasi. Frame fixture tidak membuktikan verifikasi sesi jaringan. |
| S02 | Wordmark, judul, email, password/visibility, Masuk, tautan Daftar | Hierarki dan token sesuai. Ikon tampilan memiliki tujuan nyata. Layar kosong tidak mengklaim login berhasil. |
| S03 | Nama, email, password, konfirmasi, helper, zona waktu, Buat akun | Urutan dan ukuran kontrol mengikuti kit. Capture lama yang berakhiran `end` hanya memastikan Buat akun terlihat; belum membuktikan footer “Sudah punya akun? Masuk”. Celah itu diperiksa terpisah pada APK staging. |
| S06 | Feedback simpan, ringkasan, Lihat catatan, Kembali ke beranda | Hierarki sesuai. Fixture berasal dari edit sehingga “Tidak ada XP tambahan” benar; teks +10 XP contoh HTML tidak disalin untuk edit/replay. |
| S09 | Empat metode, kategori, nilai/unit, tanggal, catatan, bantuan dan CTA bawah | Bagian awal/bawah terbaca, form dapat digulir, CTA disabled saat tidak ada perubahan. Perbedaan isi catatan antar tema berasal dari perjalanan fixture. |
| S10 | Sheet, kategori/metode, pasangan tanggal, Apply/Reset/Cancel | Implementasi modal mengikuti SCREEN_SPEC S10 meski contoh HTML memakai halaman. Aksi bawah terbaca pada kedua tema. Frame tidak membuktikan seluruh validasi rentang tanggal. |
| S25 | Fallback deteksi, fase scan, retry dan input manual | Pesan/kontrol sesuai tujuan pemulihan. Tidak menampilkan rekomendasi sukses atau membuat catatan. Status non-READY policy-08 mempunyai [bukti native tersendiri](POLICY08_ANDROID.md). |
| D01 | Kategori/tanggal, konsekuensi statistik/XP, Hapus dan Batal | Destructive memakai error, Cancel memakai secondary; teks tidak menjanjikan achievement yang capability-nya belum tersedia. Dialog dibuka, penghapusan tidak dijalankan dalam perjalanan ini. |
| D02 | Alasan masuk ulang, Keluar dan Batal | Hierarki, jarak dan dua tema sesuai. Penjelasan server berbeda dari simulasi lokal HTML secara sengaja. Dialog dibatalkan. |
| D03 | Peringatan perubahan belum tersimpan, Buang/Lanjut | Destructive dan secondary benar. Perjalanan melanjutkan editor, bukan membuang data hanya untuk screenshot. |
| D04 | Konflik awal, perbandingan versi/draf, seluruh aksi sampai bawah | Konten/aksi terbaca. Pilihan mengembalikan isian ke form untuk diperiksa; bukan klaim PATCH sukses atau overwrite otomatis. |

## Bukti dan keterikatan sumber

- [Galeri 36 PNG dan log asli](UI_SCAN_PREVIEW.md#screen-dan-dialog-tambahan) berasal dari checkpoint 6 November, API 37, 390×844 dp, font normal. Ini review ulang gambar asli; PNG tidak diubah atau diberi tanggal capture baru.
- [Manifest sumber/APK aplikasi](ui-preview/frozen-build.json) dan [APK penguji tambahan](ui-preview/remaining/test-build.json) mempertahankan identitas pengambilan lama. Hash tujuh file layar/dialog yang ditinjau masih sama dengan snapshot aplikasi tersebut. Shared focus/control dan policy scan yang berubah memiliki bukti tersendiri pada [keyboard focus](KEYBOARD_FOCUS.md), [kontrol inti](CORE_CONTROL_REVIEW.md), dan [policy-08](POLICY08_ANDROID.md).
- Referensi: [sesi terang](startup-reference-light.png), [gelap](startup-reference-dark.png), [Masuk](nav-auth-reference-login.png), [Daftar](nav-auth-reference-register.png), [edit terang](reference-S09-light.png), [gelap](reference-S09-dark.png), [simpan terang](reference-save-light.png), [gelap](reference-save-dark.png), [filter terang](reference-filter-light.png), [gelap](reference-filter-dark.png), [hapus terang](reference-delete-light.png), [gelap](reference-delete-dark.png), [keluar terang](exit-reference-light-logout.png), [gelap](exit-reference-dark-logout.png), [buang terang](exit-reference-light-discard.png), [gelap](exit-reference-dark-discard.png), [konflik terang](conflict-reference-light.png), [gelap](conflict-reference-dark.png). S25 memakai SCREEN_SPEC serta pola sheet scan sebagai acuan, bukan gambar referensi yang tidak tersedia.

Warna ikon status bar pada fixture S01–S03 lama tidak dipakai untuk menerima window produksi: fixture memanggil composable langsung dan tidak menjalankan pengaturan system bar MainActivity. Capture startup staging terbaru memperlihatkan status bar gelap pada tema terang. Keberadaan semua screen tetap **20/20 dan 4/4 dialog**, terpisah dari cakupan penerimaan visual.

## Pemeriksaan APK staging terbaru

APK pada [checkpoint 8 November](PREVIEW_APK_20261108.md), SHA `274a051b6ed81c9c189fad675dd1bf0f12606a441354037b5f4f85d42cc5d9a2`, diperiksa langsung melalui input Android pada API 37, 390×844 dp dan font 100%. Delapan PNG baru memperlihatkan window MainActivity sebenarnya, termasuk Back pada header daftar dan status bar yang mengikuti tema.

| Bagian | Terang | Gelap |
|---|---|---|
| S02 Masuk | [PNG](secondary-review/S02-light.png) | [PNG](secondary-review/S02-dark.png) |
| S03 Daftar, awal | [PNG](secondary-review/S03-light.png) | [PNG](secondary-review/S03-dark.png) |
| S03 Daftar, footer | [PNG](secondary-review/S03-end-light.png) | [PNG](secondary-review/S03-end-dark.png) |
| Pemilih tema | [PNG](secondary-review/theme-light.png) | [PNG](secondary-review/theme-dark.png) |

Pada kedua tema: buka Daftar → gulir hingga footer → pilih “Sudah punya akun? Masuk” kembali ke S02; buka Daftar lagi → Back sistem kembali ke S02. Pergantian tema berlangsung melalui dialog aplikasi, bukan perubahan resource test. Dua field login dan empat field daftar ada pada hierarchy. [Hasil dan urutan aksi](secondary-review/result.json), [manifest review/hash](secondary-review/review-manifest.json), [startup](secondary-review/launch.log).

Tidak ada submit akun, upload, atau generasi AI. Tema awal, ukuran/density dan font dipulihkan; checksum aplikasi normal sebelum/sesudah identik, staging force-stopped dan emulator QA dihentikan. Boot sempat menampilkan ANR Pixel Launcher sebelum aplikasi dibuka; dialog sistem itu dipulihkan setelah judul/bounds diperiksa dan tidak ada pada frame yang diterima.

Gate untuk cakupan ini: **Hard PASS** pada keterbacaan/scroll dan navigasi yang diamati; **Purpose PASS** karena hierarki dan token mengikuti kit; **Liveliness PASS** dengan ENERGY2/RHYTHM2/MOTION1 yang sudah dipilih; **Craft PASS** pada delapan frame staging dan frame historis yang ditinjau. Ini tidak meluluskan seluruh kombinasi error, keyboard, TalkBack atau input berisi.

## Backend dan pekerjaan berikutnya

Remote backend kembali diperiksa pada `c1e81c25d2bd24fc5e8a010cfd72217560418756`; AI `baadd773aac01f7ef48c8beb15f62875c56f6b3f`. [ANDROID.md yang dibaca](https://github.com/waste-not-app/waste-not-backend/blob/c1e81c25d2bd24fc5e8a010cfd72217560418756/docs/ANDROID.md) masih sama dengan acuan [policy-08](POLICY08_ANDROID.md). Pemeriksaan 34 artefak pin backend terhadap peer AI lulus. Tidak ada perubahan layanan/deployment atau generasi provider untuk review ini.

Checkpoint ini melengkapi review sembilan layar inti dan koreksi kontrol sebelumnya. Bagian konten/state lain, kamera aktif, navigasi dan penerimaan font/input tetap mengikuti cakupan masing-masing, kemudian pengujian fungsi/backend dilanjutkan sesuai [prioritas UI](../../UI_FIRST_PLAN.md). Migrasi preferensi ke DataStore pada backlog Pengaturan belum selesai; keberhasilan pemilih tema tidak menutup kriteria arsitektur tersebut. CI dan merge tetap diperiksa terpisah.

[CI source 9c97812](https://github.com/waste-not-app/waste-not-android/actions/runs/42412801408) selesai: lint/unit, SDK/font, seluruh varian build, environment/policy dan release packaging lulus. Kedua upload artefak gagal dengan `Artifact storage quota has been hit`. Hasil ini tidak dilaporkan sebagai CI hijau dan PR belum di-merge; pemeriksaan source yang lulus tetap berbeda dari penyimpanan artefak GitHub yang gagal.
