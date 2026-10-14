# A13 — Aturan registrasi dan timezone awal

Issue [#46](https://github.com/waste-not-app/waste-not-android/issues/46) · branch `codex/android-auth-validation-a12-a13` · **In progress**. Layar S03 belum diimplementasikan.

## Perubahan yang sudah dikerjakan

`validateRegistrationInput` memeriksa nama 2–60 karakter setelah trim, email maksimal 254 dengan pemeriksaan format, password 12–128 tanpa trim, dan konfirmasi yang sama persis. Nama/password memakai hitungan Unicode code point. Konfirmasi tetap menjadi argumen validasi client; payload register A04 hanya berisi name, email, password, dan timezone.

Hasil validasi memuat seluruh field invalid sebagai enum error, tanpa mengembalikan password/nama/email. Fungsi ini tidak menyimpan nilai atau mengirim request. Nama/email dinormalisasi pada repository; password dipertahankan persis. Batas nama profil pada DTO respons juga memakai Unicode code point agar nama yang valid saat registrasi tidak ditolak setelah API menerbitkan profil. Format email dan batas dukungannya dijelaskan pada [A12](A12-login.md).

`initialAccountTimezone(deviceTimezoneId)` memilih ID timezone yang tersedia pada perangkat atau fallback Asia/Jakarta. Fungsi menerima ID secara eksplisit; tidak menyembunyikan pembacaan perangkat atau menulis preference. Label timezone dan pilihan yang terlihat masih harus dibuat pada layar. Backend B03 tetap perlu menyepakati validasi karakter dan timezone dengan client.

## Pengujian

Tes ditambahkan untuk batas nama/password/email, karakter Unicode di luar BMP, whitespace password yang dipertahankan, mismatch konfirmasi, beberapa error bersamaan tanpa nilai sensitif, timezone IANA yang tersedia, serta fallback ID tidak didukung. Sebelas tes AccountInput lulus pada jalur native tanpa rewrite HotSwan; seluruh 73 unit test debug dan lint debug lulus. Suite sesi tetap memeriksa normalisasi email pada payload aktual serta password tanpa trim. Hook commit menjalankan ulang lint/test sebelum commit diterima.

## Pekerjaan berikutnya

- [x] Jalankan 11 tes validasi native, seluruh 73 unit test debug, dan lint debug sebelum commit.
- [ ] Buat field S03 dan pesan field Indonesia; hubungkan validator sebelum request.
- [ ] Tampilkan timezone awal dan simpan pilihan pengguna melalui payload register.
- [ ] Hubungkan EMAIL_EXISTS ke email tanpa menghapus field lain.
- [ ] Batasi password/konfirmasi pada memori; uji process death, IME, autofill, dan font besar.
- [ ] Hubungkan sesi yang diterbitkan API ke root Home dan uji Back.
- [ ] Verifikasi batas field, akun duplikat, timezone tersimpan, dan kontrak B03 nyata.
