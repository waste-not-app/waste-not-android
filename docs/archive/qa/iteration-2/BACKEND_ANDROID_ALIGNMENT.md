# Penyesuaian dokumen backend dan AI

7 November 2026. Membaca ulang [ANDROID.md pada backend 831c104](https://github.com/waste-not-app/waste-not-backend/blob/831c1045582eda8e94ef57be55bfb2ed7131e7a4/docs/ANDROID.md) dan [INTEGRATION.md pada AI 5405e15](https://github.com/waste-not-app/waste-not-ai/blob/5405e150ceab04d9154d44fb1bbb52256cbfde41/docs/INTEGRATION.md). Pemeriksa kontrak pada kedua checkout lulus, masing-masing 34 artifact dan peer. Kontrak, pin dan panduan integrasi lokal juga sama dengan remote tersebut. Commit AI setelah cb22fc6 menyentuh demo web/deployment, tanpa mengubah kontrak Android.

## Perubahan Android

- Profil menampilkan `currentStreak` dan `bestStreak` dari server, masing-masing hanya saat nilainya tersedia. Nol ditampilkan sebagai nol hari; null tidak diubah menjadi nol. Tidak menghitung streak dari halaman riwayat atau mensyaratkan flag metadata yang tidak ada. Label menjelaskan hari pencatatan menurut zona akun, sesuai S14.
- S22 memakai nama katalog yang diterjemahkan bila tersedia. Jika belum ada, label memuat nomor objek dan `raw_class` server sehingga hasil seperti `bottle` tetap terlihat. Material/kategori tetap harus dipilih dan dikonfirmasi pengguna.
- Resource pesan lama yang sudah tidak dipakai dibuang. Retry, timeout, trust TLS, input provenance dan endpoint tidak diubah untuk demo web baru.

## Pemeriksaan

Dua metode UI native API 37 lulus dalam 13,963 detik: streak 3/7 hari pada terang/gelap, current null dengan best tetap tersedia, 0/0, kedua nilai null; serta label `bottle` tanpa nama katalog dengan material belum dipilih dan tombol konfirmasi nonaktif. Ini fixture komponen UI, bukan akun dengan streak nyata atau hasil inference produksi. Aplikasi pengujian dibangun dengan hot reload mati dan dummy data false; bukti jaringan nyata disimpan pada [checkpoint kamera/AI](AI_LIVE_UI.md).

[Log](backend-contract-preview/native.log), [hash APK/sumber dan konfigurasi display](backend-contract-preview/result.json), serta [enam gambar beserta hash](backend-contract-preview/captures.json) disimpan. Percobaan awal ditolak karena dialog ANR System UI emulator menutupi aplikasi; log diagnostik disimpan di luar repo. Pengulangan hanya dilakukan setelah dialog sistem hilang. Konfigurasi display dipulihkan dan emulator milik pengujian ditutup sesudahnya.

| Keadaan | Bukti |
|---|---|
| Streak server | [Terang](backend-contract-preview/S14-streak-light.png), [gelap](backend-contract-preview/S14-streak-dark.png) |
| Nol diketahui | [0/0 hari](backend-contract-preview/S14-streak-zero.png) |
| Belum diketahui | [Streak tidak ditampilkan](backend-contract-preview/S14-streak-unknown.png) |
| Label mentah, material belum dipilih | [Panel S22](backend-contract-preview/S22-raw-label-panel.png) |

## Unduhan SDK tes pada CI

Run CI `42334440013` gagal pada pengambilan SDK Robolectric oleh `MavenArtifactFetcher`, sebelum assertion aplikasi. Repo tes kini ditetapkan ke Maven Central `https://repo.maven.apache.org/maven2` melalui properti resmi `robolectric.dependency.repo.url`; kegagalan tes mencetak exception penuh. Tidak ada tes yang dilewati atau dependensi versi baru.

Pengulangan `NavGraphRobolectricRenderTest` lulus dengan direktori Maven lokal baru, sehingga SDK benar-benar diunduh kembali. SHA512 JAR cocok dengan checksum resmi Maven Central. [Hasil](backend-contract-preview/robolectric-result.json). Ini membuktikan endpoint berhasil pada pemeriksaan lokal, bukan jaminan semua unduhan CI mendatang. Run CI sebelumnya juga gagal mengunggah artifact karena kuota penyimpanan; perubahan repository SDK tidak menyelesaikan kuota tersebut.

## Batas layanan yang tetap berlaku

Android hanya mengakses HTTPS Go, bukan worker/demo AI atau kunci provider. READY belum menyimpan aktivitas; konfirmasi dan jumlah tetap dibutuhkan. Empat policy guide AI tidak mengisi katalog artikel umum yang masih kosong. Achievement, foto permanen, fasilitas, reset password dan hapus akun tetap mengikuti capability layanan, bukan ditampilkan sebagai fitur siap pakai. Perubahan ini tidak membuktikan seluruh penerimaan visual, semua kondisi AI, perangkat fisik atau rilis main sudah selesai.
