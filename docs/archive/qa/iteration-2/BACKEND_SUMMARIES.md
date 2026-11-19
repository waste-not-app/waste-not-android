# Binding ringkasan backend

Branch `codex/android-backend-summaries` melanjutkan checkpoint catatan. Acuan kontrak adalah backend `5cc59bc461b81c45e08878da2c44a8b3e9eef15b`, `contracts/core.openapi.json` dan handler `internal/api/statistics.go`.

## Data yang tersedia

- `CoreRepository.dashboard()` membaca tanggal/zona akun, ringkasan hari ini, tiga catatan terbaru dan progres server dalam satu respons.
- `CoreRepository.statistics(range)` mengirim batas tanggal inklusif yang dipilih; respons harus memakai rentang yang sama.
- DTO memetakan total ditangani, Reduce yang dihindari, gram dan benda ke field terpisah. Angka agregat dan jumlah aktivitas/metode memakai Long. Recycling rate memakai nilai desimal server dengan basis HANDLED_ACTIVITY_COUNT dan mempertahankan null ketika denominator nol.
- Statistik memerlukan setiap tanggal berurutan pada rentang, seluruh kategori tanpa duplikasi dan seluruh metode. Nilai negatif, zona tidak valid, angka rate berupa string, basis salah atau respons sukses selain 200 menghasilkan MALFORMED_RESPONSE.
- Endpoint memakai guard/tag sesi dan pemetaan error yang sama dengan catatan. Tidak menghitung total dari tiga catatan terbaru atau satu halaman riwayat.

Model baru berada di paket `model`, terpisah dari kalkulasi dataset dummy. Beranda, Statistik dan Profil remote masih perlu dihubungkan ke model ini, dengan refresh setelah simpan/hapus dan perlindungan pergantian akun. Default dummy dan penerimaan visual [Iteration 2](../../tasks/ITERATION_2_DESIGN.md) tetap mengikuti checkpoint desain.

## Verifikasi lokal

Build native remote, lint debug dan 190 debug unit tests lulus: 182 yang ada serta delapan kasus baru `ServerSummaryTest`, tanpa gagal/error/skip. Kasus baru memeriksa:

1. Total 25 catatan tetap berasal dari server ketika daftar terbaru hanya tiga item; progres dan zona akun dipertahankan.
2. Query from/to, pemisahan Reduce, berat/benda, basis rate, kategori/metode nol serta urutan tanggal.
3. Agregat 5 miliar gram dan 3 miliar aktivitas/metode tidak terpotong ke Int.
4. Periode kosong memuat seluruh tanggal/kategori dengan nol dan rate null.
5. Statistik yang tanggal/kategori/metodenya tidak lengkap, duplikat, negatif atau salah tipe tidak menjadi empty success.
6. Respons untuk rentang lain dan status 202/201 tidak diterima.
7. Dashboard menolak catatan duplikat/lebih dari tiga, tanggal mendatang terhadap tanggal respons, zona catatan berbeda dan progres malformed.
8. Error validasi mempertahankan field/request ID; server error dan konfigurasi kosong tetap eksplisit.

## Database/perangkat

Perluasan `RemoteActivityLifecycleTest` lulus pada API 24 terhadap Go/PostgreSQL di `cp-bcc`, 5 November 2026: satu kasus, 11 tahap, 1,663 detik. Kanal pengembangan memakai tunnel SSH loopback yang sama dengan [checkpoint catatan](BACKEND_RECORDS.md#lifecycle-native-ke-database-lulus-melalui-ssh).

Selain register/login dan lifecycle catatan, tes memeriksa dashboard kosong dan statistik dua hari; total 250 gram, metode/kategori, satu hari nol dan 10 XP sesudah create/replay; perpindahan total dari berat menjadi 3 benda sesudah PATCH; serta seluruh total/progres kembali nol dan rate null setelah DELETE. Tanggal create berasal dari tanggal akun yang dikembalikan dashboard server. Model statistik tidak menebak tanggal dari perangkat.

[Log native](backend-summaries/native-lifecycle.log), [hasil dan cleanup](backend-summaries/native-lifecycle-result.json), serta [manifest](backend-summaries-checkpoint.json) mengikat APK dan sumber yang diuji. Satu akun fixture unik dibuat untuk pengujian ini. Setelahnya akun dihapus berdasarkan email/nama/ID persis; sesi, catatan dan operasi AI milik ID itu masing-masing nol. Password/token hanya dalam memori. Tunnel dan emulator milik tes sudah dihentikan; density/font scale tidak diubah. Counter audit/rate-limit normal tetap mengikuti TTL server.

Hasil ini membuktikan binding repository/session/perangkat ke database. Penerimaan Beranda/Statistik/Profil melalui UI, refresh/pergantian akun dan koneksi HTTPS publik masih terbuka. Gerbang sertifikat Android 7 tetap mengikuti [QA catatan](BACKEND_RECORDS.md). CI checkpoint berikutnya belum dinyatakan lulus dari hasil lokal ini.
