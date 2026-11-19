# A13 — Aturan registrasi dan timezone awal

Issue [#46](https://github.com/waste-not-app/waste-not-android/issues/46) · **Siap review client**. Validator awal ada pada branch `codex/android-auth-validation-a12-a13`; wiring UI pada `codex/android-auth-navigation`.

## Wiring S03 15 Oktober 2026

Form berisi nama/email/password/konfirmasi dan timezone IANA awal yang terlihat. Password baru memakai ContentType.NewPassword; konfirmasi hanya di client. Validasi seluruh boundary memakai AccountInput yang sudah diuji; EMAIL_EXISTS dikaitkan dengan email dan tidak menghapus field lain. Adapter API tetap mendelegasikan register ke SessionRepository; demo secara eksplisit berlabel contoh dan tidak menerbitkan token. Tidak menambah checkbox dokumen yang belum tersedia. Uji VM penolakan invalid/duplikat serta tes perangkat fokus error, mismatch, IME dan sukses root lulus dengan gateway fixture. Hasil final ada di [laporan QA](ITERATION_1_QA.md).

## Perubahan yang sudah dikerjakan

`validateRegistrationInput` memeriksa nama 2–60 karakter setelah trim, email maksimal 254 dengan pemeriksaan format, password 12–128 tanpa trim, dan konfirmasi yang sama persis. Nama/password memakai hitungan Unicode code point. Konfirmasi tetap menjadi argumen validasi client; payload register A04 hanya berisi name, email, password, dan timezone.

Hasil validasi memuat seluruh field invalid sebagai enum error, tanpa mengembalikan password/nama/email. Fungsi ini tidak menyimpan nilai atau mengirim request. Nama/email dinormalisasi pada repository; password dipertahankan persis. Batas nama profil pada DTO respons juga memakai Unicode code point agar nama yang valid saat registrasi tidak ditolak setelah API menerbitkan profil. Format email dan batas dukungannya dijelaskan pada [A12](A12-login.md).

`initialAccountTimezone(deviceTimezoneId)` memilih ID timezone yang tersedia pada perangkat atau fallback Asia/Jakarta. Fungsi menerima ID secara eksplisit; tidak menulis preference. Form menampilkan ID tersebut dan mengirim timezone melalui register; tes perangkat memeriksa timezone yang diterima gateway sama dengan profil sesudah sukses. Persistensi server tetap bagian integrasi B03.

## Pengujian

Tes ditambahkan untuk batas nama/password/email, karakter Unicode di luar BMP, whitespace password yang dipertahankan, mismatch konfirmasi, beberapa error bersamaan tanpa nilai sensitif, timezone IANA yang tersedia, serta fallback ID tidak didukung. Sebelas tes AccountInput lulus pada jalur native tanpa rewrite HotSwan; seluruh 73 unit test debug dan lint debug lulus. Suite sesi tetap memeriksa normalisasi email pada payload aktual serta password tanpa trim. Hook commit menjalankan ulang lint/test sebelum commit diterima.

## Pekerjaan berikutnya

- [x] Jalankan 11 tes validasi native, seluruh 73 unit test debug, dan lint debug sebelum commit.
- [x] Buat field S03 dan pesan field Indonesia; hubungkan validator sebelum request.
- [x] Tampilkan timezone awal dan kirim melalui payload register; persistensi backend belum diklaim.
- [x] Hubungkan EMAIL_EXISTS ke email tanpa menghapus field lain.
- [x] Batasi password/konfirmasi pada memori; IME/validasi/font besar dan hilangnya password pada process death diuji. Autofill ContentType tersedia; layanan password manager pihak ketiga belum diuji.
- [x] Hubungkan state sesi gateway ke root Home; adapter produksi mendelegasikan penerbitan sesi ke repository, sementara uji UI memakai fixture eksplisit.
- [ ] Verifikasi batas field, akun duplikat, timezone tersimpan, dan kontrak B03 nyata.
