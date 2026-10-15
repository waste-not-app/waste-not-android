# A12 — Validasi akun untuk layar Masuk

Issue [#35](https://github.com/waste-not-app/waste-not-android/issues/35) · **Siap review client**. Validator awal ada pada branch `codex/android-auth-validation-a12-a13`; wiring UI pada `codex/android-auth-navigation`.

## Wiring S02 15 Oktober 2026

AuthScreen menyediakan email/password, visibility toggle, email keyboard, Next/Done, autofill ContentType dan submit loading/disabled. Tidak memblokir paste atau menampilkan lupa password yang belum tersedia. Validasi memakai AccountInput sebelum gateway dipanggil; field error memfokuskan isian pertama. Credential salah memakai pesan umum, jaringan/error penyimpanan terpisah. Password hanya di AuthUiState ViewModel, tidak di Saver/SavedState/storage; toString state meredaksinya dan sukses mengosongkan password.

Sembilan tes VM lulus, termasuk double tap satu request, email/password bertahan saat error, password tanpa trim, dan cancellation lama tidak membuka submit baru. Root auth diganti entry akun saat sukses. Lihat data contoh memilih gateway terpisah secara eksplisit; tidak mengklaim autentikasi API. Tes perangkat memeriksa IME, loading/error, fokus dan batas tombol terhadap keyboard nyata pada font 200%. Hasil serta batas autofill/TalkBack ada di [laporan QA](ITERATION_1_QA.md).

## Perubahan yang sudah dikerjakan

`validateLoginInput(email, password)` memeriksa email kosong/format/panjang dan batas password 12–128 karakter sesuai PRD §8. Email di-trim dan dinormalisasi dengan Locale.ROOT; password tidak di-trim. Panjang password dihitung sebagai Unicode code point sehingga karakter di luar BMP tidak dihitung dua kali.

Hasil berupa map field ke enum error tanpa menyertakan input atau credential. Validator tidak melakukan request atau menyimpan nilai. Normalisasi email yang sama sudah dipakai oleh SessionRepository login/register sehingga tidak bergantung bahasa perangkat.

Format email memeriksa local part ASCII tanpa quoting, titik yang valid, serta domain DNS/IDN melalui [java.net.IDN dengan aturan STD3](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/IDN.html). Panjang local part mengikuti [RFC 5321 §4.5.3.1.1](https://www.rfc-editor.org/rfc/rfc5321.html#section-4.5.3.1.1); panjang alamat dibatasi 254 setelah normalisasi dan konversi domain untuk pemeriksaan. Quoted/SMTPUTF8 local part masih menunggu kontrak B03. Validasi ini tidak mengirim pengecekan DNS atau membuktikan kepemilikan email.

Tes batas 254 karakter menemukan PatternsCompat.EMAIL_ADDRESS 1.18.0 membatasi label domain setelah label pertama hingga 26 karakter. Validasi kini memakai batas label DNS sehingga subdomain panjang yang valid tidak ditolak. Validasi server dan status credential tetap menjadi tanggung jawab backend. AuthScreen kini memanggil validator melalui SessionViewModel sebelum request.

## Pengujian

Tes ditambahkan untuk batas password/email, email malformed dan whitespace internal, email plus/subdomain, normalisasi pada locale Turki, serta hasil error tanpa credential. Sebelas tes AccountInput lulus pada jalur native tanpa rewrite HotSwan; seluruh 73 unit test debug dan lint debug lulus. Suite sesi tetap memeriksa normalisasi email pada payload aktual serta password tanpa trim. Hook commit menjalankan ulang lint/test sebelum commit diterima.

## Pekerjaan berikutnya

- [x] Jalankan 11 tes validasi native, seluruh 73 unit test debug, dan lint debug sebelum commit.
- [x] Buat S02 dan hubungkan validator ke pesan Indonesia dalam strings.xml.
- [x] Hubungkan request login, loading/disable submit, error credential umum, dan error jaringan tanpa menghapus email.
- [x] Pasang email keyboard, IME, paste/autofill ContentType serta tampil/sembunyikan password; integrasi layanan password manager pihak ketiga belum diuji.
- [x] Pastikan password hanya state memori dan tidak pulih setelah process death; proses background dibunuh, task sama dipulihkan dengan PID baru dan field kosong.
- [x] Hubungkan sesi berhasil/banner sesi berakhir ke root A09; single flight diuji VM dan loading/Back diuji perangkat memakai sumber contoh/fixture.
- [ ] Verifikasi login dengan backend B03 nyata.
