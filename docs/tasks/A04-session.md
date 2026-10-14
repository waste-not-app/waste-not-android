# A04 — Credential storage dan refresh sesi

Issue [#21](https://github.com/waste-not-app/waste-not-android/issues/21) · branch `codex/android-session-a04` · **In progress**.

## Storage dan credential

- `RefreshTokenStore` dengan operasi suspend read/write/clear dan hasil absent/available/invalidated. Token meredaksi `toString`; opaque token dibatasi 8192 karakter ASCII tanpa whitespace sebagai batas defensif format storage.
- `KeystoreRefreshTokenStore` memakai kunci AES-256 Android Keystore, GCM dengan nonce acak dan tag 128-bit. Factory produksi menempatkan ciphertext di `Context.noBackupFilesDir`; backup/transfer juga telah dinonaktifkan pada A01.
- Penulisan ciphertext memakai AtomicFile, dengan mutex per instance dan operasi crypto/file pada dispatcher IO milik coroutine pemanggil. File memuat versi, nonce, panjang, dan ciphertext; tidak memuat password atau access token.
- Ciphertext corrupt/tampered atau kunci hilang membatalkan credential dan menghapus file/kunci terkait. Kegagalan storage dilaporkan, tanpa fallback plaintext. File sementara/backup ikut dihapus saat clear.
- Runner instrumentasi Android ditetapkan eksplisit dan empat tes perangkat ditambahkan: round-trip/random nonce/non-exportable key, tampering, kunci hilang, dan penghapusan idempoten.

## Lifecycle sesi dan API auth

- `SessionRepository` menyediakan login/register/bootstrap, request akun terautentikasi, dan logout sebagai API suspend. Password dipertahankan apa adanya; nama/email dinormalisasi saat membangun DTO di repository. UI tidak perlu membuat DTO HTTP.
- Access token hanya berada dalam record privat di memori. StateFlow hanya memuat fase sesi, profil yang sudah diverifikasi, dan epoch akun; tidak memuat password/access/refresh token. DTO credential dan tipe token meredaksi toString.
- `RequestCredential` adalah tag lokal untuk membekukan token/epoch/revisi pada satu request. Interceptor tetap memeriksa origin/prefix API; tag tidak mengizinkan credential ke URL eksternal. Endpoint auth publik tetap tanpa bearer.
- Beberapa 401 berbagi mutex refresh. Respons 401 dari token lama memakai hasil rotasi yang sudah selesai; request diulang paling banyak sekali. Payload write dan client_request_id dipertahankan identik. 401 kedua mengakhiri sesi.
- Refresh expired/revoked, timeout, hasil malformed/rotasi tidak pasti, atau token yang tidak berganti menghapus credential dan menghasilkan SignedOut. Pergantian user yang diklaim respons refresh ditolak. Session ID boleh diperbarui server karena kontrak tidak mensyaratkan SID tetap; identitas akun/epoch tetap dibatasi.
- Cold start tanpa credential langsung SignedOut tanpa request. Saat perangkat diketahui offline, bootstrap mempertahankan credential terenkripsi dan menghasilkan ConnectionRequired. Setelah rotasi berhasil, GET /me harus memverifikasi identitas sebelum profil diterbitkan. Retry kegagalan GET /me memakai token yang sudah diperoleh, tanpa rotasi berulang.
- Logout menghapus akses lokal lebih dahulu, membatalkan job akun, lalu mencoba pencabutan menggunakan credential lama yang ditangkap. Pencabutan tidak ikut dibatalkan saat akun baru login atau scope layar berakhir; timeout client membatasi request. Hanya HTTP 204 menghasilkan serverRevoked=true. Status hasil logout lama tidak menimpa akun baru.
- Mutex persistence dan pemeriksaan epoch mencegah write rotasi lama memulihkan credential akun A atau menghapus credential akun B. Callback cleanup menerima user ID serta epoch untuk namespace yang sesuai; cache/draf/reminder/AI yang belum diimplementasikan belum memiliki cleanup produksi.
- `WasteNotApplication` menyediakan satu `AppContainer` per proses. Container menghubungkan network, Keystore store, session repository dan core repository. Tidak ada coroutine/request otomatis dari constructor atau init.

Root navigasi dan ViewModel akun harus memakai pasangan `(user.id, epoch)` dari SessionState.Authenticated. Token refresh mempertahankan epoch; login ulang, termasuk user yang sama, membuat lifetime akun baru. Wiring root/private back stack dikerjakan A09/A11/A29 dan belum dianggap selesai oleh kode repository.

Primitive mengikuti [Android Keystore](https://developer.android.com/privacy-and-security/keystore) dan [contoh AES/GCM pada KeyGenParameterSpec](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec). Tidak ada klaim kunci hardware-backed/StrongBox pada semua perangkat.

## Bukti pengujian saat ini

`lintDebug testDebugUnitTest assembleDebug assembleDebugAndroidTest` lulus: 62 unit test, tanpa failure/error, termasuk 26 tes sesi, 17 jaringan, 16 domain, dan 3 starter/generated. Lima tes perangkat dijalankan pada emulator medium_phone API 36 x86_64 dengan AndroidJUnitRunner: **OK (5 tests)**, durasi tes 4,109 detik. Empat tes membaca ciphertext aktual, memeriksa key non-exportable, serta memverifikasi invalidasi dan penghapusan file/kunci; satu tes memastikan Application membagikan container yang sama dan menolak request privat sebelum sesi terverifikasi.

```sh
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.wastenot.data.local.KeystoreRefreshTokenStoreTest,com.example.wastenot.data.local.ApplicationContainerTest
```

Pada pemeriksaan ini APK dipasang dan runner dipanggil melalui adb dengan selector class yang sama. Perangkat minimum API 24 belum diuji. AppContainer telah terhubung; root navigasi dan layar auth belum memakai implementasi sesi.

CI juga membangun dan menyimpan APK instrumentasi agar kode tes perangkat diperiksa kompilasinya. Tes perangkat dijalankan lokal pada emulator; workflow saat ini belum menjalankan emulator otomatis di GitHub.

26 tes SessionRepository memakai MockWebServer dan store fixture: login/register, password tanpa trim, state tanpa credential/offline, verifikasi identitas, concurrent 401, retry kedua, revoked/timeout/malformed refresh, owner/UUID, single-flight login, logout online/offline, rotasi terlambat saat akun berganti, pembatalan login saat write storage, kegagalan storage, lifetime baru untuk user yang sama, dan replay payload/idempotency ID. Suite ini lulus pada jalur native dengan `-Photswan.dispatchRewriteEnabled=false`; bytecode SessionRepository diperiksa tanpa referensi interpreter. Seluruh suite debug dan lima tes instrumentasi (empat Keystore + satu Application/container) diverifikasi terpisah.

Perbaikan yang ditemukan oleh tes: logout semula ikut job akun yang dibatalkan login baru; retry bootstrap offline masih memanggil /me; perbedaan kapitalisasi UUID dianggap identitas berbeda; write storage yang selesai setelah login dibatalkan masih menerbitkan credential. Keempatnya memiliki tes regresi. Pemeriksaan cancellation setelah write sekarang memicu cleanup sebelum token dipublikasikan. Build juga mengungkap batas bridge value class HotSwan 2.0.1; hanya SessionTokens yang dikecualikan dari hot reload, dengan rincian di [DEV_PLUGINS.md](../DEV_PLUGINS.md).

Fixture [auth-session.json](../../app/src/test/resources/contracts/auth-session.json), [auth-refresh.json](../../app/src/test/resources/contracts/auth-refresh.json), dan [me.json](../../app/src/test/resources/contracts/me.json) adalah data simulasi untuk kontrak Android. Bentuk respons refresh memakai token, expiry dan session_id, dengan user opsional; B03/B04 harus memverifikasi shape ini bersama backend. Tes client tidak membuktikan backend deployed, signature JWT, ownership server, atau pencabutan sesi server nyata.

## Checklist yang masih terbuka

- [x] Jalankan tes Keystore pada perangkat dan periksa ciphertext/penghapusan. Factory memakai noBackupFilesDir; aturan backup A01 telah diperiksa pada APK.
- [x] Implementasikan access token hanya di memori serta pemetaan DTO auth tanpa kebocoran credential.
- [x] Serialisasi refresh ketika beberapa request memperoleh 401; retry request semula maksimal sekali.
- [x] Hapus credential dan terbitkan SignedOut saat refresh revoked/expired/timeout atau rotasi tidak pasti, tanpa loop.
- [x] Batalkan pekerjaan akun dan cegah respons terlambat memulihkan credential/identitas setelah logout/ganti akun.
- [x] Hubungkan session repository, network, dan Keystore melalui Application/AppContainer.
- [x] Periksa debug penuh, regresi pembatalan storage, serta tes Application/container pada perangkat.
- [ ] Hubungkan startup/login/logout dan root/private back stack pada UI A09/A11/A12/A13/A29.
- [ ] Verifikasi kontrak dan perilaku backend B03/B04 nyata, termasuk pencabutan server.

Checkbox issue belum menyatakan task selesai. Catatan ini diperbarui setelah bukti berikutnya tersedia.
