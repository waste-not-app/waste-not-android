# Review sebelum merge Iteration 1

15 Oktober 2026. Cakupan: PR #88–#95, perubahan dari `main` pada `e923f3c` sampai branch `codex/android-auth-navigation`. Pemeriksaan membaca diff, caller, spesifikasi, tes, konfigurasi build dan workflow rilis. Sumber contoh/backend/AI mengikuti izin pengguna; tidak dianggap integrasi server nyata.

## Standards

Tidak ditemukan penghalang pada penyimpanan Keystore/backup, batas bearer API, refresh terserialisasi, cancellation/epoch, root identitas, atau lifetime cache yang diperiksa. README dan CLAUDE sebelumnya masih menyebut auth/navigasi belum terhubung; status keduanya diperbarui berdasarkan kode dan bukti pengujian. Beban yang ditinjau adalah satu proses aplikasi Android dengan request akun yang dapat berjalan bersamaan.

## Spec

Tiga koreksi client diselesaikan sebelum merge:

| Kasus | Sebelum | Sesudah dan bukti |
|---|---|---|
| Catatan 500 karakter dengan whitespace luar | Ditolak karena batas dihitung sebelum trim; payload menyimpan whitespace luar | EditorDraft men-trim sekali sebelum validasi/payload. Tes mencakup create/edit, batas 500/501, spasi internal, whitespace-only dan draf raw tetap utuh; PRD §8 |
| Ringkasan 1.655 g | Memakai presisi detail: 1,655 kg | Kedua ringkasan handled/avoided memakai presisi ringkasan: 1,66 kg. SummaryPrecisionTest memeriksa teks layar sebenarnya; PRD §9.2 |
| Edit catatan demo yang baru dibuat | updated_at dapat mendahului created_at dan tidak maju pada edit berikutnya | created_at dipertahankan; updated_at maju setiap edit. Tes memeriksa dua edit, version 3 dan jumlah aktivitas tetap; FR-11 |

Pemeriksaan ulang terhadap koreksi ini tidak menemukan temuan tersisa pada diff yang ditinjau. Integrasi write/ringkasan server, konten panduan, AI, serta glass opsional tetap mengikuti batas implementasi pada task masing-masing.

## Verifikasi

- **101 unit test native, 0 gagal/error/skip**; termasuk dua regresi baru untuk catatan dan timestamp demo.
- **2 tes perangkat API 24 lulus, 13,389 detik**: SummaryPrecisionTest serta create/edit/dirty Back/logout dari AppNavigationTest.
- Gate APK native lulus: minSdk 24, MainActivity adjustResize, lima font dan OFL.
- Hook wajib lint/test berjalan saat commit. Status workflow terbaru serta hasil merge dicatat pada [PR #95](https://github.com/waste-not-app/waste-not-android/pull/95), agar dokumen tidak mengklaim CI untuk head berbeda.

Riwayat bukti perangkat lebih luas tersedia di [QA Iteration 1](ITERATION_1_QA.md). Review ini tidak mengklaim tes backend deployed, kualitas suara TalkBack, animasi predictive per frame, atau layanan password manager eksternal.

## Integrasi PR

Semua PR diarahkan ke main. PR #95 memuat seluruh commit pendahulu; merge commit mempertahankan riwayat dan memasukkan snapshot lengkap ke main dalam satu push. Sesudah merge, status PR #88–#95 dan kesetaraan tree main dengan tree yang diuji harus diperiksa. Workflow main otomatis membangun dan menerbitkan APK, sehingga hasil CI serta release/deploy diperiksa setelah merge.
