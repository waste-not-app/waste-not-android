# Progres Android WasteNot

Snapshot checklist GitHub: 2026-11-05T17:34:56.112050+00:00. Cakupan pekerjaan tetap seluruh backlog Android, kesesuaian design system/mockup termasuk navbar dan navigasi, integrasi backend serta AI. Sumber visual dan penerimaan menyeluruh berada pada [matriks Iteration 2](ITERATION_2_DESIGN.md); urutan integrasi pada [peta backend](BACKEND_INTEGRATION.md).

## Angka backlog yang dapat dihitung

| Ukuran | Tercentang | Total | Persentase |
|---|---:|---:|---:|
| Seluruh butir checklist awal Android | 140 | 417 | 33,6% |
| Checklist implementasi | 94 | 260 | 36,2% |
| Checklist bukti penerimaan | 46 | 157 | 29,3% |

Ada 52 issue Android; delapan memiliki checklist awal tercentang seluruhnya. Seluruh issue masih terbuka dan ditugaskan ke `oktavsm`. [Snapshot angka](android-progress-baseline.json) merekam dasar perhitungan.

Persentase dihitung dari checkbox pada bagian backlog awal, sebelum marker `wastenot-backlog`. Riwayat komentar/progres tidak dihitung ulang. Angka ini menunjukkan status checklist yang tercatat; setiap klaim selesai tetap memerlukan bukti sesuai lingkupnya. Persentase kesiapan rilis belum ditetapkan. Kriteria tambahan dari audit desain atau kontrak backend/AI harus dicatat dan denominator diperbarui ketika cakupannya berubah.

## Checkpoint yang sudah memiliki bukti

| Pekerjaan | Bukti | Penerimaan berikutnya |
|---|---|---|
| Transport catatan backend | [PR #97](https://github.com/waste-not-app/waste-not-android/pull/97), head `76f2b19`: CI push/PR termasuk upload APK/laporan lulus; 182 unit dan lifecycle API 24 ke Go/PostgreSQL lewat SSH | Screen remote dan rangkaian akun/draf lengkap; API 24 menjadi bukti historis setelah minimum dinaikkan ke API 30 |
| Binding dashboard/statistik | [PR #98](https://github.com/waste-not-app/waste-not-android/pull/98), head `eae6345`: CI push/PR termasuk upload lulus; 190 unit dan 11 tahap lifecycle native ke database | Hubungkan Beranda/Statistik/Profil ke snapshot server dan refresh setelah mutasi |
| Pemulihan write belum pasti | Branch `codex/android-write-outcome-recovery`: tiga regresi awal gagal; perbaikan lulus 196 unit dan 23 eksekusi native, termasuk PID berbeda sesudah force-stop serta font 200%; [QA](../qa/iteration-2/WRITE_RECOVERY.md) | CI push/PR #99 lulus; alur HTTP melalui screen masih diverifikasi per checkpoint |
| Layar ringkasan server | [PR #100](https://github.com/waste-not-app/waste-not-android/pull/100), head `90e04c5`: CI push/PR dan artifact lulus, 202 debug unit tests, MainActivity/HTTPS publik API 30 dan API 37, sembilan eksekusi komponen API 37 terang/gelap/font 200%; [QA](../qa/iteration-2/SERVER_SCREENS.md) | Penerimaan luas; pergantian hari otomatis, Riwayat, Panduan/AI tetap terbuka |
| API Panduan | Branch `codex/android-guide-api`: daftar/detail terhubung ke CoreRepository; 212 unit tests, satu alur publik API 37 dengan katalog kosong dan 10 eksekusi komponen normal/font 200% lulus; [QA](../qa/iteration-2/GUIDE_API.md) | CI head Panduan, kurasi dan detail/prefill positif produksi; seluruh gate visual tetap terbuka |
| Screen/navbar/navigasi | Bukti per checkpoint tertaut di matriks Iteration 2 | Seluruh state, scroll, tema, ukuran teks, perangkat dan alur tetap mengikuti matriks lengkap |

## Prioritas lanjutan

1. Lengkapi penerimaan pemulihan write melalui layar HTTP; logika UUID/payload/draf dan bukti restart proses sudah tersedia pada PR #99 dengan CI lulus.
2. Verifikasi checkpoint Beranda/Statistik/Profil terhadap server publik pada API 30 dan Android terbaru; lengkapi pergantian hari otomatis, cursor/filter/pull-to-refresh Riwayat serta pemisahan akun.
3. Integrasikan Panduan PUBLISHED beserta sumber/review, lalu katalog/deteksi/rekomendasi AI publik melalui Go. Key dan layanan Python tetap privat; tidak auto-save/XP dari scan.
4. Lengkapi migrasi penyimpanan, lifecycle kamera/permission, capability opsional dan audit seluruh screen/control/navigasi terhadap kit.
5. Jalankan penerimaan HTTPS publik pada Android 11/API 30 dan Android terbaru, serta seluruh gate visual. Android 7/API 24 tidak lagi menjadi syarat rilis sesuai keputusan pengguna.

## Daftar task awal

| Issue | Task | Checklist tercentang |
|---|---|---:|
| [#3](https://github.com/waste-not-app/waste-not-android/issues/3) | [Android][A01] Rapikan fondasi aplikasi dan konfigurasi build yang sudah ada | 8/8 |
| [#5](https://github.com/waste-not-app/waste-not-android/issues/5) | [Android][A02] Model domain, parser jumlah dan format tanggal/satuan | 8/8 |
| [#6](https://github.com/waste-not-app/waste-not-android/issues/6) | [Android][A03] HTTP client, DTO, metadata dan pemetaan error API | 8/8 |
| [#7](https://github.com/waste-not-app/waste-not-android/issues/7) | [Android][A06] Lengkapi WasteNotTheme, font dan token terang/gelap | 7/8 |
| [#10](https://github.com/waste-not-app/waste-not-android/issues/10) | [Android][A07] Komponen input, pilihan kategori/metode dan tindakan form | 8/8 |
| [#11](https://github.com/waste-not-app/waste-not-android/issues/11) | [Android][A08] Komponen ringkasan, daftar, progres dan feedback | 0/8 |
| [#21](https://github.com/waste-not-app/waste-not-android/issues/21) | [Android][A04] Penyimpanan token aman dan refresh sesi terserialisasi | 8/8 |
| [#27](https://github.com/waste-not-app/waste-not-android/issues/27) | [Android][A05] Draf per akun, process death dan pembatalan pekerjaan akun | 3/8 |
| [#28](https://github.com/waste-not-app/waste-not-android/issues/28) | [Android][A09] Root auth, lima tab dan perilaku Back Navigation 3 | 8/8 |
| [#33](https://github.com/waste-not-app/waste-not-android/issues/33) | [Android][A10] Dock lima tujuan dan action utama Catat/Scan | 0/8 |
| [#34](https://github.com/waste-not-app/waste-not-android/issues/34) | [Android][A11] S01 Pemulihan sesi dan retry validasi identitas | 8/8 |
| [#35](https://github.com/waste-not-app/waste-not-android/issues/35) | [Android][A12] S02 Masuk: validasi, password manager dan error sesi | 8/8 |
| [#36](https://github.com/waste-not-app/waste-not-android/issues/36) | [Android][A15] S05 Editor catatan manual dan validasi lengkap | 3/8 |
| [#37](https://github.com/waste-not-app/waste-not-android/issues/37) | [Android][A18] S07 Riwayat berkelompok, pagination dan state daftar | 2/8 |
| [#38](https://github.com/waste-not-app/waste-not-android/issues/38) | [Android][A20] S08 Detail catatan dan state tidak tersedia | 3/8 |
| [#39](https://github.com/waste-not-app/waste-not-android/issues/39) | [Android][A25] S11 Panduan: pencarian, kategori dan hasil kosong | 5/8 |
| [#40](https://github.com/waste-not-app/waste-not-android/issues/40) | [Android][A29] D02 Keluar, pencabutan sesi dan pembersihan lokal | 7/8 |
| [#41](https://github.com/waste-not-app/waste-not-android/issues/41) | [Android][A30] S16 Pengaturan tema, informasi aplikasi dan privasi | 5/8 |
| [#46](https://github.com/waste-not-app/waste-not-android/issues/46) | [Android][A13] S03 Daftar: aturan akun dan timezone yang terlihat | 7/9 |
| [#47](https://github.com/waste-not-app/waste-not-android/issues/47) | [Android][A14] S04 Beranda: sambungkan HomeScreen awal ke data akun nyata | 5/8 |
| [#48](https://github.com/waste-not-app/waste-not-android/issues/48) | [Android][A16] S05 Simpan idempoten dan pemulihan hasil POST yang belum pasti | 4/8 |
| [#49](https://github.com/waste-not-app/waste-not-android/issues/49) | [Android][A19] S10 Filter Riwayat sebagai bottom sheet | 4/8 |
| [#50](https://github.com/waste-not-app/waste-not-android/issues/50) | [Android][A21] S09 Ubah catatan dengan version dan aturan tanggal lama | 3/8 |
| [#51](https://github.com/waste-not-app/waste-not-android/issues/51) | [Android][A22] D01 Hapus catatan, If-Match dan efek agregat | 1/8 |
| [#52](https://github.com/waste-not-app/waste-not-android/issues/52) | [Android][A23] D03 Buang draf untuk Back dan pergantian alur | 0/8 |
| [#53](https://github.com/waste-not-app/waste-not-android/issues/53) | [Android][A26] S12 Detail panduan, sumber dan prefill editor | 4/8 |
| [#54](https://github.com/waste-not-app/waste-not-android/issues/54) | [Android][A27] S13 Statistik 7/30 hari, chart dan alternatif teks | 7/8 |
| [#55](https://github.com/waste-not-app/waste-not-android/issues/55) | [Android][A28] S14 Profil, XP/level dan capability progres | 3/8 |
| [#56](https://github.com/waste-not-app/waste-not-android/issues/56) | [Android][A33] Adaptasi ukuran, edge-to-edge dan keyboard | 0/8 |
| [#57](https://github.com/waste-not-app/waste-not-android/issues/57) | [Android][A49] S17 Pengingat opt-in lokal dan pembatalan saat logout | 0/8 |
| [#61](https://github.com/waste-not-app/waste-not-android/issues/61) | [Android][A17] S06 Feedback catatan tersimpan pada detail | 3/8 |
| [#62](https://github.com/waste-not-app/waste-not-android/issues/62) | [Android][A24] D04 Konflik perubahan tanpa overwrite atau kehilangan draf | 0/8 |
| [#63](https://github.com/waste-not-app/waste-not-android/issues/63) | [Android][A31] S20 Terapkan state loading/kosong/error/offline di semua layar | 0/8 |
| [#64](https://github.com/waste-not-app/waste-not-android/issues/64) | [Android][A32] Invalidasi create/edit/delete dan pergantian hari akun | 0/8 |
| [#65](https://github.com/waste-not-app/waste-not-android/issues/65) | [Android][A35] Ukur performa daftar 1.000 catatan dan perangkat minimum | 0/8 |
| [#66](https://github.com/waste-not-app/waste-not-android/issues/66) | [Android][A38] State machine satu camera bottom sheet dan action dari lima tab | 0/8 |
| [#67](https://github.com/waste-not-app/waste-not-android/issues/67) | [Android][A48] S15 Achievement dan streak setelah core stabil | 0/8 |
| [#69](https://github.com/waste-not-app/waste-not-android/issues/69) | [Android][A34] TalkBack, fokus, kontras dan semantics aplikasi | 0/8 |
| [#70](https://github.com/waste-not-app/waste-not-android/issues/70) | [Android][A36] Regresi core, isolasi dua akun dan journey staging | 0/8 |
| [#71](https://github.com/waste-not-app/waste-not-android/issues/71) | [Android][A39] S21 CameraX: izin, preview dan lifecycle perangkat | 0/8 |
| [#75](https://github.com/waste-not-app/waste-not-android/issues/75) | [Android][A37] Lengkapi CI, APK demo dan panduan handoff | 0/8 |
| [#76](https://github.com/waste-not-app/waste-not-android/issues/76) | [Android][A40] S21 Capture, tinjau foto, orientasi dan cleanup lokal | 0/8 |
| [#77](https://github.com/waste-not-app/waste-not-android/issues/77) | [Android][A50] S18 Lampiran Photo Picker, upload privat dan retry | 0/8 |
| [#78](https://github.com/waste-not-app/waste-not-android/issues/78) | [Android][A51] S19 Titik daur ulang dengan dataset terverifikasi | 0/8 |
| [#79](https://github.com/waste-not-app/waste-not-android/issues/79) | [Android][A41] Repository AI: upload eksplisit, katalog, expiry dan error | 0/8 |
| [#80](https://github.com/waste-not-app/waste-not-android/issues/80) | [Android][A52] Kesiapan distribusi publik: privasi, reset password dan hapus akun | 0/8 |
| [#81](https://github.com/waste-not-app/waste-not-android/issues/81) | [Android][A42] S22 Review bbox, pilih satu objek dan koreksi material | 0/8 |
| [#82](https://github.com/waste-not-app/waste-not-android/issues/82) | [Android][A43] S23 Kondisi independen dan pembatalan rekomendasi lama | 0/8 |
| [#83](https://github.com/waste-not-app/waste-not-android/issues/83) | [Android][A44] S24 Review saran, sumber dan penanganan khusus | 0/8 |
| [#84](https://github.com/waste-not-app/waste-not-android/issues/84) | [Android][A45] S25 Fallback AI dan capability off tanpa menghalangi manual | 0/8 |
| [#85](https://github.com/waste-not-app/waste-not-android/issues/85) | [Android][A46] S24 ke S05: jumlah manual, provenance dan satu log final | 0/8 |
| [#86](https://github.com/waste-not-app/waste-not-android/issues/86) | [Android][A47] Regresi AI pada perangkat fisik dan E2E staging | 0/8 |
