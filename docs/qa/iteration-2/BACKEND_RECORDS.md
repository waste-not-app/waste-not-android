# Transport catatan ke backend

Checkpoint pada branch `codex/android-backend-records`, berbasis `ef4fcca`, 4 November 2026. Branch ini melanjutkan hasil UI tanpa mengubah layout atau mengganti adapter default. Backend core diperiksa pada [`28a3cb78031c1c60f687ab2567aa2a5c4de41260`](https://github.com/waste-not-app/waste-not-backend/tree/28a3cb78031c1c60f687ab2567aa2a5c4de41260), terutama `docs/API.md`, `contracts/core.openapi.json` dan `internal/api/logs.go`.

## Perilaku yang tersambung

| Operasi | Kontrak Android |
|---|---|
| Detail | GET `waste-logs/{id}`, status 200, ID respons harus sama dengan UUID yang diminta |
| Simpan | POST `waste-logs`, UUID draf tetap pada `client_request_id`; 201 berarti baru, 200 berarti replay |
| Edit | PATCH `waste-logs/{id}`, versi yang dibaca ikut dikirim; hasil harus mempunyai ID sama dan versi lebih baru |
| Hapus | DELETE `waste-logs/{id}`, header `If-Match: "<version>"`; hanya 204 dianggap berhasil |

Request memakai field kategori/metode/jumlah/tanggal/catatan dari `ActivityInput`, tanpa owner, zona akun atau timestamp buatan client. Perubahan satuan mengirim field unit lain sebagai null, memakai helper Quantity yang sudah ada. Adapter meneruskan server/domain error, bukan menggantinya dengan data dummy.

`ApiExecutor` memakai satu jalur penanganan error/koneksi/cancellation untuk envelope JSON dan respons kosong. Mapper create menerima status agar replay tidak dianggap catatan baru; sukses 202 atau respons yang tidak dapat dikonfirmasi menjadi MALFORMED_RESPONSE. UUID/versi lokal yang tidak valid tidak dikirim.

Semua panggilan memakai credential sesi yang sama dengan jalur read. Refresh setelah 401 tetap diserialisasi oleh `SessionRepository`; percobaan setelah refresh memakai snapshot payload yang sama. Transport tidak mengulang write saat koneksi putus. Retry setelah hasil write tidak diketahui tetap keputusan pemanggil dengan UUID/payload draf yang sama. `clear()` tidak menghapus data server.

## Bukti

Build native API 24 menggunakan JDK 17, `-Pwastenot.hotReload=false`, `-Pwastenot.useDummyData=false` dan base URL HTTPS backend. Build, lint dan **182 debug unit tests lulus**, nol gagal/error/skip. Default property pada source tetap dummy.

Sebelas kasus baru `RemoteActivityStoreTest` memakai MockWebServer untuk menguji:

- simpan 201 dan replay 200 dengan payload/UUID sama;
- identitas detail, UUID huruf besar, dan penolakan hasil milik ID lain;
- perubahan berat ↔ benda, null field unit lain, dan version PATCH;
- If-Match terkutip, 204, replay delete dan penolakan 200 yang tidak sesuai kontrak;
- konflik idempotensi/versi, log terhapus, validasi, 404/428, request ID dan Retry-After;
- hasil 202, versi stale, quantity XOR rusak dan body detail kosong;
- respons create yang hilang, retry eksplisit dan cancellation tanpa replay transport;
- input lokal tidak valid, konfigurasi tidak tersedia, serta credential akun dan payload setelah refresh 401.

[Manifest](backend-records-checkpoint.json) mengikat source, kontrak, hasil unit, konfigurasi build remote dan APK pengujian. [Laporan sebelas kasus](backend-records/RemoteActivityStoreTest.xml) merekam hasil test runner. Ini bukti binding/transport dengan server tiruan dan kompilasi varian remote, **belum** penerimaan alur perangkat terhadap database/deployment backend.

## Uji HTTPS native: belum lulus pada API 24

Pemeriksaan baca-saja `RemoteReadOnlyContractTest` pada emulator API 24 gagal sebelum respons HTTP. Targetnya metadata anonim yang semestinya 401. Hasil aktual adalah `SSLHandshakeException` dengan alert handshake failure saat ClientHello. Kegagalan diulang dengan APK yang sama, lalu direkam handshake-nya pada 5 November 2026. DNS dan ping perangkat berhasil; proxy global tidak dikonfigurasi.

| Pemeriksaan | Hasil aktual |
|---|---|
| ClientHello emulator | TLS 1.2, SNI benar, suite ECDHE-ECDSA AES-GCM tersedia, supported group hanya `0x0017` / P-256 |
| Sertifikat publik gateway | Kunci ECDSA `secp384r1` / P-384, issuer Let's Encrypt YE1 |
| Host TLS 1.2 dengan grup default / P-384 | Handshake berhasil |
| Host TLS 1.2 dibatasi ke P-256 / X25519 | Handshake failure, sesuai pola perangkat |

[ClientHello yang difilter ke endpoint](backend-records/clienthello-api24.json), [uji pembanding grup host](backend-records/tls-groups.json) dan [log native](backend-records/native-readonly-failure.log) memuat bukti. PCAP mentah tetap di artifact root eksternal, tidak dibundel. Standar TLS 1.2 mensyaratkan kunci EC sertifikat server mengikuti grup yang didukung client; lihat [RFC 8422 §5.3](https://www.rfc-editor.org/rfc/rfc8422.html#section-5.3).

Gerbang yang masih perlu diselesaikan: gateway menyediakan sertifikat P-256 atau RSA yang kompatibel, lalu rantai sertifikat dan koneksi diverifikasi lagi pada API 24. Lokasi/pengelola gateway masih diminta kepada pengguna; dokumentasi backend hanya menunjukkan Nginx HTTP di belakang gateway. Verifikasi sertifikat/hostname tetap memakai perilaku OkHttp/platform, dan minimum API 24 dipertahankan. Uji native gagal ini tidak dihitung sebagai kelulusan atau diganti dengan uji host.

Reproduksi setelah memasang APK remote dan APK penguji yang cocok:

```sh
adb -s emulator-5554 shell am instrument -w -r \
  -e verifyLiveReadOnly true \
  -e class com.example.wastenot.data.network.RemoteReadOnlyContractTest \
  com.example.wastenot.test/androidx.test.runner.AndroidJUnitRunner
```

Kasus ini hanya aktif ketika argumen tersebut diberikan. Ia melakukan GET metadata anonim dan mempertahankan kegagalan TLS sebagai kegagalan tes. Tidak membuat akun atau melakukan write pada deployment.

## Penerimaan yang masih diperlukan

Dashboard/statistik/progres harus memakai agregat server; satu halaman 20 catatan tidak boleh dianggap keseluruhan data. Pagination/filter, Panduan PUBLISHED dan AI publik masih mengikuti [peta integrasi](../../tasks/BACKEND_INTEGRATION.md). Lanjutkan penerimaan auth → create/replay → detail/edit/konflik/delete → agregat → logout/pergantian akun pada perangkat dengan backend. Jangan menyatakan mode remote siap dari hasil unit atau readiness saja.

Penerimaan seluruh screen/navbar/navigasi tetap berada pada [matriks Iteration 2](../../tasks/ITERATION_2_DESIGN.md). Hasil visual sebelumnya tetap berlaku pada source checkpoint masing-masing dan tidak dihitung ulang dalam angka transport ini.
