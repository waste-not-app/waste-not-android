# A11: Pemulihan sesi startup

Issue [#34](https://github.com/waste-not-app/waste-not-android/issues/34) · **Siap review client**. Bagian repository disediakan oleh [PR #91](https://github.com/waste-not-app/waste-not-android/pull/91) dan [A04](A04-session.md).

## Wiring S01 15 Oktober 2026

MainNavigation kini memulai bootstrap sekali melalui LaunchedEffect pada SessionViewModel yang bertahan saat rotasi. S01 menampilkan wordmark/progress dan Menyiapkan catatanmu tanpa delay buatan. Koneksi yang tidak dapat memvalidasi identitas memberi Coba lagi/Masuk ulang; busy bootstrap menampilkan progress, dan retry memakai guard satu job. Root diganti dari state, bukan event sekali lewat; entry lama ditolak jika tidak cocok dengan identitas saat ini. Tes VM bootstrap/retry dan tes perangkat pemulihan identitas lulus; hasil akhir ada di [laporan QA](ITERATION_1_QA.md).

## Perubahan yang sudah dikerjakan

RemoteAccountSession membaca snapshot jaringan default aktif dari ConnectivityManager setiap bootstrap/retry. Izin ACCESS_NETWORK_STATE bersifat normal. Tanpa jaringan aktif, repository mempertahankan credential terenkripsi dan menampilkan ConnectionRequired tanpa mengirim refresh; snapshot bukan bukti API sehat. Tes gateway mengubah snapshot offline ke online: permintaan awal nol, credential tetap ada, retry kemudian mengirim tepat satu refresh. Startup memakai safeDrawingPadding dan verticalScroll; retry/masuk ulang dapat dijangkau pada landscape pendek dan font 200%.

`SessionRepository.bootstrap()` membaca credential terenkripsi secara eksplisit dari coroutine pemanggil. Tanpa credential, hasilnya SignedOut tanpa request atau delay buatan. Credential invalid menghasilkan alasan masuk ulang. Saat perangkat diketahui offline, credential tetap terenkripsi dan state ConnectionRequired tidak membawa profil pengguna.

Pemulihan online melakukan refresh kemudian GET /me. Profil hanya diterbitkan setelah identitas terverifikasi; request privat ditolak selama identitas belum pasti. Bila rotasi sudah berhasil tetapi GET /me gagal, retry memakai access token yang sudah diperoleh. Hint offline pada retry tidak memanggil /me atau merotasi ulang. Rotasi revoked, timeout, atau tidak pasti menghapus credential; tidak ada loop refresh otomatis.

## Bukti dan batas

Tes repository dalam suite A04 memeriksa absent/invalid credential tanpa HTTP, bootstrap offline, publikasi identitas setelah /me, retry setelah rotasi, dan penolakan identitas berbeda. Seluruh 26 tes sesi lulus pada jalur native; seluruh 62 unit test dan lint debug lulus. Tes Application/container pada API 36 memastikan request privat ditolak sebelum sesi terverifikasi.

Bukti repository di atas memakai fixture. Tes layar tambahan memeriksa ConnectionRequired menyembunyikan profil A, retry menuju auth expired, dan root akun berikutnya terpisah. Backend nyata tetap diuji pada integrasi. Detail kontrak ada di [A04-session.md](A04-session.md).

## Pekerjaan berikutnya

- [x] Buat S01 dengan wordmark dan status sesuai kit, tanpa splash timer.
- [x] Panggil bootstrap dari lifecycle UI yang jelas; tampilkan Coba lagi dan Masuk ulang sesuai state.
- [x] Hubungkan root auth/private dengan A09; lifetime akun memakai `(user.id, epoch)`.
- [x] Verifikasi root replacement, rotasi, Back, dan larangan data privat sebelum identitas pasti pada perangkat dengan gateway fixture.
- [ ] Verifikasi endpoint B03/B04 dan GET /me pada backend nyata.
