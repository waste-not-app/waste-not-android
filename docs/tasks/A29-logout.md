# A29 — Logout dan pembersihan sesi

Issue [#40](https://github.com/waste-not-app/waste-not-android/issues/40) · **Siap review client**. Bagian repository disediakan oleh [PR #91](https://github.com/waste-not-app/waste-not-android/pull/91) dan [A04](A04-session.md).

## Wiring D02 15 Oktober 2026

Profil membuka dialog Keluar; cancel hanya menutup dialog. Confirm menghapus isian auth dan memanggil gateway logout. UI membedakan Pending dari hasil localCleared/serverRevoked; tidak mengklaim rahasia sudah dihapus hanya karena phase SignedOut muncul lebih dulu. Kegagalan cleanup menyediakan retry; hasil lama tidak mengganti status setelah login/sumber baru. Revocation lama tidak memblokir login berikutnya.

Entry root akun dipop dan WorkspaceViewModel.onCleared menghapus records, replay cache dan state draf/hasil. Lima back stack privat berada dalam lifetime entry akun. Reminder/foto/AI masih tidak aktif sehingga tidak ada resource fitur tersebut yang berjalan. Uji VM cleanup false, hasil lama dan login selama revocation lulus; tes perangkat memeriksa cancel/confirm dan hilangnya catatan privat. Demo tidak pernah diklaim mencabut sesi server. Hasil final ada di [laporan QA](ITERATION_1_QA.md).

## Perubahan yang sudah dikerjakan

`SessionRepository.logout()` segera membatalkan akses lokal dan job akun, membersihkan refresh token, lalu mencoba pencabutan server dengan credential lama yang ditangkap. Hanya HTTP 204 menghasilkan `serverRevoked=true`; jaringan gagal menghasilkan local-only tanpa klaim pencabutan server. Hasil juga memuat `localCleared` agar UI tidak menyatakan penghapusan berhasil ketika storage gagal.

Request pencabutan dan cleanup kritis menyelesaikan pekerjaan dalam NonCancellable, dengan batas waktu network client. Login akun baru tidak membatalkan pencabutan lama; hasil logout lama tidak menimpa state akun baru. Epoch dan mutex persistence mencegah write token terlambat memulihkan akun lama atau menghapus credential akun baru.

Callback cleanup menerima user ID dan epoch yang akan dibersihkan. Cache/draf UI aktif kini dibersihkan lewat lifetime entry akun. Draf persisten, reminder, serta foto/sesi AI belum aktif; cleanup resource tersebut harus dipasang bersamaan fiturnya.

## Bukti dan batas

Tes repository memeriksa pembatalan request akun, bearer lama pada logout 204, logout saat koneksi putus, lifetime baru ketika user yang sama login ulang, serta rotasi terlambat bersamaan dengan logout/login akun B. Seluruh 26 tes sesi native dan 62 unit test debug lulus. Tes Keystore API 36 memeriksa penghapusan ciphertext/file sementara/alias secara idempoten.

HTTP 204 di sini berasal dari MockWebServer; pencabutan refresh session backend nyata belum terbukti. Detail bukti ada di [A04-session.md](A04-session.md).

## Pekerjaan berikutnya

- [x] Buat dialog D02, aksi cancel, dan penjelasan logout sesuai kit.
- [x] Hubungkan hasil local-only/storage failure/confirmed ke pesan UI yang benar.
- [x] Hapus root/private back stack dan pastikan Back tidak membuka layar pribadi.
- [ ] Pasang cleanup cache/draf/reminder/AI ketika fitur tersebut aktif.
- [ ] Verifikasi pencabutan server B04 dan isolasi tampilan akun A/B secara nyata.
