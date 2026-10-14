# A04 — Credential storage dan refresh sesi

Issue [#21](https://github.com/waste-not-app/waste-not-android/issues/21) · branch `codex/android-session-a04` · **In progress**.

## Yang sudah ditulis

- `RefreshTokenStore` dengan operasi suspend read/write/clear dan hasil absent/available/invalidated. Token meredaksi `toString`; opaque token dibatasi 8192 karakter ASCII tanpa whitespace sebagai batas defensif format storage.
- `KeystoreRefreshTokenStore` memakai kunci AES-256 Android Keystore, GCM dengan nonce acak dan tag 128-bit. Factory produksi menempatkan ciphertext di `Context.noBackupFilesDir`; backup/transfer juga telah dinonaktifkan pada A01.
- Penulisan ciphertext memakai AtomicFile, dengan mutex per instance dan operasi crypto/file pada dispatcher IO milik coroutine pemanggil. File memuat versi, nonce, panjang, dan ciphertext; tidak memuat password atau access token.
- Ciphertext corrupt/tampered atau kunci hilang membatalkan credential dan menghapus file/kunci terkait. Kegagalan storage dilaporkan, tanpa fallback plaintext. File sementara/backup ikut dihapus saat clear.
- Runner instrumentasi Android ditetapkan eksplisit dan empat tes perangkat ditambahkan: round-trip/random nonce/non-exportable key, tampering, kunci hilang, dan penghapusan idempoten.

Primitive mengikuti [Android Keystore](https://developer.android.com/privacy-and-security/keystore) dan [contoh AES/GCM pada KeyGenParameterSpec](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec). Tidak ada klaim kunci hardware-backed/StrongBox pada semua perangkat.

## Bukti pengujian saat ini

`assembleDebug assembleDebugAndroidTest` lulus. Empat tes Keystore dijalankan pada emulator medium_phone API 36 x86_64 dengan AndroidJUnitRunner: **OK (4 tests)**, durasi tes 2,662 detik. Tes membaca ciphertext aktual, memeriksa key non-exportable, serta memverifikasi invalidasi dan penghapusan file/kunci.

```sh
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.wastenot.data.local.KeystoreRefreshTokenStoreTest
```

Pada pemeriksaan ini APK dipasang dan runner dipanggil melalui adb dengan selector class yang sama. Perangkat minimum API 24 belum diuji. Implementasi belum dipasang di AppContainer atau alur auth.

## Checklist yang masih terbuka

- [x] Jalankan tes Keystore pada perangkat dan periksa ciphertext/penghapusan. Factory memakai noBackupFilesDir; aturan backup A01 telah diperiksa pada APK.
- [ ] Implementasikan access token hanya di memori serta pemetaan DTO auth tanpa kebocoran credential.
- [ ] Serialisasi refresh ketika beberapa request memperoleh 401; retry request semula maksimal sekali.
- [ ] Hapus sesi saat refresh revoked/expired/timeout atau rotasi tidak pasti, tanpa loop.
- [ ] Batalkan pekerjaan akun dan cegah respons terlambat memulihkan akun setelah logout/ganti identitas.
- [ ] Integrasikan session repository dengan startup/login/logout; verifikasi kontrak backend B03/B04.

Checkbox issue belum menyatakan task selesai. Catatan ini diperbarui setelah bukti berikutnya tersedia.
