# A11 — Pemulihan sesi startup

Issue [#34](https://github.com/waste-not-app/waste-not-android/issues/34) · **In progress**. Bagian repository disediakan oleh [PR #91](https://github.com/waste-not-app/waste-not-android/pull/91) dan [A04](A04-session.md); layar S01 belum dibuat.

## Perubahan yang sudah dikerjakan

`SessionRepository.bootstrap()` membaca credential terenkripsi secara eksplisit dari coroutine pemanggil. Tanpa credential, hasilnya SignedOut tanpa request atau delay buatan. Credential invalid menghasilkan alasan masuk ulang. Saat perangkat diketahui offline, credential tetap terenkripsi dan state ConnectionRequired tidak membawa profil pengguna.

Pemulihan online melakukan refresh kemudian GET /me. Profil hanya diterbitkan setelah identitas terverifikasi; request privat ditolak selama identitas belum pasti. Bila rotasi sudah berhasil tetapi GET /me gagal, retry memakai access token yang sudah diperoleh. Hint offline pada retry tidak memanggil /me atau merotasi ulang. Rotasi revoked, timeout, atau tidak pasti menghapus credential; tidak ada loop refresh otomatis.

## Bukti dan batas

Tes repository dalam suite A04 memeriksa absent/invalid credential tanpa HTTP, bootstrap offline, publikasi identitas setelah /me, retry setelah rotasi, dan penolakan identitas berbeda. Seluruh 26 tes sesi lulus pada jalur native; seluruh 62 unit test dan lint debug lulus. Tes Application/container pada API 36 memastikan request privat ditolak sebelum sesi terverifikasi.

Ini bukti state repository dengan fixture, bukan bukti perpindahan layar, cache akun produksi, atau backend nyata. Detail pengujian dan kontrak ada di [A04-session.md](A04-session.md).

## Pekerjaan berikutnya

- [ ] Buat S01 dengan wordmark dan status sesuai kit, tanpa splash timer.
- [ ] Panggil bootstrap dari lifecycle UI yang jelas; tampilkan Coba lagi dan Masuk ulang sesuai state.
- [ ] Hubungkan root auth/private dengan A09; lifetime akun memakai `(user.id, epoch)`.
- [ ] Verifikasi root replacement, rotasi, Back, dan larangan data privat sebelum identitas pasti pada perangkat.
- [ ] Verifikasi endpoint B03/B04 dan GET /me pada backend nyata.
