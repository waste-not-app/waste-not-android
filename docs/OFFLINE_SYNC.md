# Aktivitas offline dan sinkronisasi

Aktivitas dibaca dari Room, termasuk ketika jaringan tersedia. Create, update, dan delete menyimpan perubahan lokal dan operasi antrean dalam satu transaksi. WorkManager mengirim antrean ketika jaringan tersedia. Panduan dan AI tetap memakai layanan online.

## Penyimpanan dan akun

`activities.db` menyimpan aktivitas, antrean operasi, receipt create, snapshot server dan identitas akun terakhir yang sudah diverifikasi. Setiap aktivitas, operasi, receipt dan snapshot memiliki pemilik. Refresh/access token serta password tidak masuk database atau input worker; refresh token tetap dienkripsi dengan Android Keystore.

Akun yang pernah diverifikasi dapat membuka data lokal tanpa jaringan jika credential Keystore masih tersedia. Keadaan ini tidak menghasilkan access token dan tidak dapat memanggil API terautentikasi. Setelah tersambung, refresh dan `/me` harus memverifikasi akun sebelum sinkronisasi. Login pertama membutuhkan koneksi.

Keluar menghapus identitas aktif dan credential. Antrean milik akun tetap tersimpan agar dapat dilanjutkan setelah akun yang sama masuk lagi; dialog keluar menjelaskan hal tersebut. Worker akun A tidak boleh berjalan di bawah akun B. Database dan credential dikecualikan dari backup/cloud/transfer melalui aturan backup yang sudah ada.

Penghapusan akun berbeda dari keluar: setelah server mengonfirmasi DELETE akun dan sesi/work akun dihentikan, `ActivityDao.clearOwner` menghapus aktivitas, antrean, receipt, snapshot dan identitas pemilik tersebut dalam satu transaksi. Data pemilik lain tidak ikut dihapus. Operasi ini tidak dipakai saat logout biasa.

## Alur perubahan

1. Create menyimpan UUID request, payload asli, identitas lokal dan operasi CREATE. Receipt menjaga UUID/payload tetap sama setelah respons hilang atau aplikasi dibuka ulang.
2. Edit/delete mengantre setelah operasi sebelumnya pada aktivitas yang sama. Identitas lokal tetap dapat ditemukan ketika server sudah memberikan ID sendiri. Perubahan UI memakai revisi lokal; permintaan ke server memakai versi server tersimpan.
3. Saat akan dikirim, versi permintaan dibekukan dalam antrean. Respons POST yang hilang diulang dengan UUID dan payload yang sama. Respons PATCH yang hilang hanya dianggap sudah diterima jika GET memperlihatkan tepat versi berikutnya dengan seluruh isian yang sama.
4. Pengakuan operasi lama tidak mengganti isian lokal yang mempunyai operasi lebih baru. Penghapusan lokal disembunyikan dari riwayat sambil menunggu pengakuan server.
5. Kesalahan jaringan, server dan rate limit menggunakan retry/backoff WorkManager. Kegagalan permanen dan konflik disimpan untuk ditinjau pengguna.

Antrean work menggunakan nama unik per akun dan APPEND_OR_REPLACE agar perubahan yang datang ketika worker sebelumnya selesai tetap mendapat giliran. Satu worker menyinkronkan seluruh operasi yang tersedia saat ia mulai; pekerjaan berikutnya juga menarik keadaan server terbaru.

## Konflik dan provenance AI

409 tidak menimpa server atau menghapus isian lokal. Panel sinkronisasi menampilkan isian lokal dan versi server yang sudah dibaca. Pengguna dapat mengonfirmasi pengiriman ulang berdasarkan versi yang ditinjau, atau membuang perubahan lokal. Jika server berubah lagi, pengguna diminta meninjau lagi. Penghapusan yang berkonflik ikut tampil di panel meskipun sudah disembunyikan dari riwayat.

Referensi AI yang sudah kedaluwarsa/tidak valid tetap menyimpan isian aktivitas. Pengguna dapat memilih secara eksplisit untuk menyimpannya sebagai catatan manual. Konversi membuat UUID baru setelah penolakan definitif dari server, menghapus provenance AI, dan tidak mengklaim rekomendasi masih berlaku.

## Pembacaan, ringkasan dan batas cache

Riwayat menggunakan pagination lokal 20 baris beserta filter kategori, metode dan tanggal. Sinkronisasi latar belakang mengambil halaman server secara bertahap, juga 20 baris. Riwayat baru lengkap setelah penarikan pertama selesai; label aplikasi menyatakan jika penarikan itu belum selesai. Data yang belum pernah diunduh belum tersedia offline.

Penarikan tidak menimpa aktivitas yang masih mempunyai perubahan lokal. Pagination server bukan snapshot database, sehingga aktivitas yang tidak terlihat di hasil penarikan diverifikasi melalui GET sebelum dihapus dari cache. Penarikan terputus tidak menghapus cache yang belum terlihat.

Ringkasan, statistik, XP dan streak memakai snapshot terakhir yang dikonfirmasi server. Aktivitas terbaru di beranda dibaca dari Room. Simpan lokal tidak mengumumkan tambahan XP. Panel sinkronisasi menjelaskan perbedaan perubahan lokal dan ringkasan server, termasuk ketika koneksi tersedia tetapi belum ada pengakuan server.

Snapshot yang dibaca dari Room membawa waktu konfirmasi server dan penanda cache. Beranda, profil dan statistik menampilkan penanda tersebut; data dari hari/periode sebelumnya diberi peringatan, bukan dianggap hasil hari ini. Kegagalan refresh disimpan terpisah dari payload sehingga snapshot lama tetap bisa dibaca tanpa menyembunyikan kesalahan. Pada pergantian hari, statistik dapat menampilkan periode tersimpan dengan panjang yang sama dan tanggal aslinya.

Pengakuan CREATE, UPDATE atau DELETE menyimpan revisi acak pada metadata snapshot dalam transaksi pengakuan yang sama. `ActivityStore.synchronizationAcknowledgements` memutar ulang revisi terakhir milik akun, dengan deduplikasi; simpan lokal, penarikan riwayat dan refresh ringkasan tidak mengubah revisi ini. `AchievementsViewModel` menerima flow tersebut dan memuat ulang pencapaian yang sudah diminta setelah pengakuan, termasuk jika worker berjalan lebih lambat daripada simpan lokal atau ViewModel baru mulai mengamati. Tidak ada tambahan XP yang dihitung lokal.

## Verifikasi

Tes host Room mencakup pembukaan ulang file database, retry respons hilang, urutan create/edit/delete, perlindungan perubahan lokal, filter/pagination, receipt, konflik dan isolasi pemilik. Tes sesi memastikan identitas offline tidak memberikan credential API dan logout menghapus identitas aktif.

Pengujian perangkat hanya memakai HP fisik yang diizinkan pemilik. `OfflineActivityDeviceTest` bersifat opt-in melalui `verifyOfflineLive=true`, membuat akun backend terpisah, memakai database/alias Keystore khusus tes dan membersihkan catatan tes. Skenario kegagalan transport memakai socket loopback tertutup agar koneksi wireless ADB tetap hidup; ini bukan pengujian mode pesawat. `ActivitySyncUiTest` memeriksa pesan simpan lokal dan keputusan konflik serta menghasilkan screenshot. Tidak ada emulator yang digunakan.

Schema Room diekspor ke `app/schemas/`. Perubahan schema berikutnya wajib memiliki migrasi yang mempertahankan antrean; jangan menambahkan fallback destructive migration.

Jika APK yang sudah terpasang memakai kunci rilis, gunakan `-Pwastenot.debugApplicationIdSuffix=.offlineqa` saat membuat APK debug dan debugAndroidTest. Keduanya dipasang berdampingan sebagai WasteNot QA sehingga aplikasi utama dan datanya tidak perlu dihapus. Konfigurasi debug standar, staging dan release tetap memakai ID biasanya.

Untuk pengujian proses mati, jalankan metode persistent-offline dengan `offlinePhase=prepare` dan UUID `offlineRunId`. Simpan laporan `cache/qa/offline-sync/device.json`, hentikan hanya proses paket `.offlineqa`, kemudian jalankan metode yang sama memakai `offlinePhase=recover`, `offlineRunId` yang sama dan `offlineRequestId` dari `local_id` laporan. Fase prepare sengaja mempertahankan database dan credential khusus tes; fase recover memulihkannya lalu membersihkannya. Jangan menjalankan tes WorkManager produksi terhadap paket aplikasi utama; tes tersebut mensyaratkan suffix `.offlineqa`.
