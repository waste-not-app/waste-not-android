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

[Manifest](backend-records-checkpoint.json) mengikat source, kontrak, hasil unit, konfigurasi build remote dan APK pengujian. [Laporan sebelas kasus](backend-records/RemoteActivityStoreTest.xml) merekam hasil test runner. Angka unit ini berasal dari server tiruan; penerimaan perangkat/database dicatat terpisah berikut ini.

### Koreksi skenario koneksi putus di CI

CI head `945f601` gagal pada kedua event ([PR](https://github.com/waste-not-app/waste-not-android/actions/runs/41945791629), [push](https://github.com/waste-not-app/waste-not-android/actions/runs/41945780072)): retry eksplisit dalam tes kehilangan respons masih mengembalikan Failure, sehingga cast langsung ke Success gagal. Hasil itu tidak dihitung sebagai CI lulus. Kasus tunggal, seluruh 11 kasus catatan dan probe 40 pengulangan lokal tidak mereproduksi kegagalan tersebut.

Skenario tes kini mengosongkan pool koneksi setelah gangguan buatan, sebelum menguji retry eksplisit pada koneksi baru. Assertion juga melaporkan hasil dan jumlah request sebelum cast, serta membatasi waktu tunggu request. [MockWebServer 4.12.0](https://github.com/square/okhttp/blob/parent-4.12.0/mockwebserver/src/main/kotlin/okhttp3/mockwebserver/MockWebServer.kt) memang menutup socket untuk DISCONNECT_AFTER_REQUEST. Perubahan ini memperjelas prasyarat koneksi pulih dalam tes; aturan transport produksi tidak diubah. Kelulusan CI head perbaikan tetap harus diperiksa terpisah dari probe lokal dan tes backend asli.

## Lifecycle native ke database: lulus melalui SSH

Pada 5 November 2026, satu kasus `RemoteActivityLifecycleTest` lulus pada emulator API 24 dalam 1,538 detik terhadap aplikasi Go dan PostgreSQL deployment `cp-bcc`. Kanal pengembangan: emulator `10.0.2.2:18088` → port loopback host → tunnel SSH → Go `127.0.0.1:18080`. Tunnel hanya bind loopback, lalu dihentikan setelah tes. HTTP lokal memakai izin debug yang sudah ada; kebijakan HTTPS publik dan release tidak diperlonggar.

Tes memakai satu akun sementara dengan email unik `example.test` dan password acak dalam memori, bukan akun pengguna. Tanggal aktivitas memakai tanggal aktual dalam zona akun Asia/Jakarta. Tujuh tahap lulus:

1. Register akun uji.
2. Logout server lalu login kembali ke identitas sama.
3. Metadata terautentikasi.
4. Create 201, replay eksplisit 200 dengan ID/payload sama, detail dan daftar.
5. Edit berat menjadi 3 benda; PATCH dan DELETE memakai versi lama menghasilkan konflik.
6. Progres `/me` menjadi 1 catatan/10 XP, lalu delete, detail 404, replay create 410, replay delete 204, daftar kosong dan progres 0/0.
7. Logout membersihkan credential lokal dan token akses lama ditolak server.

[Log native](backend-records/native-lifecycle.log) dan [hasil beserta cleanup](backend-records/native-lifecycle-result.json) merekam bukti. Driver host memverifikasi email fixture belum ada sebelum tes, lalu menghapus hanya identitas persis tersebut beserta ID yang dikembalikan register. Pemeriksaan sesudahnya: akun fixture tidak ada; sesi, catatan dan operasi AI milik ID itu masing-masing nol. Password/token tidak ditulis ke bukti. Counter rate limit/audit normal tidak dihapus. Konfigurasi deployment dan data akun lain tidak diubah.

APK aplikasi sama dengan checkpoint transport (`0b90f41f…`); APK tes baru terikat pada manifest. Tes ini opt-in melalui `verifyLiveFixture=true`, `fixtureRunId=<UUID>` dan `recordsTestBaseUrl=http://10.0.2.2:18088/v1/`. Jalankan hanya dengan tunnel dan driver cleanup akun fixture yang sudah disiapkan. CI tidak membuat akun live. Ini bukti repository/session dari perangkat ke database, **belum** koneksi HTTPS publik, interaksi screen remote, refresh nyata, pergantian akun atau agregat dashboard/statistik.

## Uji HTTPS native: belum lulus pada API 24

Pemeriksaan baca-saja `RemoteReadOnlyContractTest` pada emulator API 24 gagal sebelum respons HTTP. Targetnya metadata anonim yang semestinya 401. Hasil aktual adalah `SSLHandshakeException` dengan alert handshake failure saat ClientHello. Kegagalan diulang dengan APK yang sama, lalu direkam handshake-nya pada 5 November 2026. DNS dan ping perangkat berhasil; proxy global tidak dikonfigurasi.

| Pemeriksaan | Hasil aktual |
|---|---|
| ClientHello emulator | TLS 1.2, SNI benar, suite ECDHE-ECDSA AES-GCM tersedia, supported group hanya `0x0017` / P-256 |
| Sertifikat publik gateway | Kunci ECDSA `secp384r1` / P-384, issuer Let's Encrypt YE1 |
| Host TLS 1.2 dengan grup default / P-384 | Handshake berhasil |
| Host TLS 1.2 dibatasi ke P-256 / X25519 | Handshake failure, sesuai pola perangkat |

[ClientHello yang difilter ke endpoint](backend-records/clienthello-api24.json), [uji pembanding grup host](backend-records/tls-groups.json) dan [log native](backend-records/native-readonly-failure.log) memuat bukti. PCAP mentah tetap di artifact root eksternal, tidak dibundel. Standar TLS 1.2 mensyaratkan kunci EC sertifikat server mengikuti grup yang didukung client; lihat [RFC 8422 §5.3](https://www.rfc-editor.org/rfc/rfc8422.html#section-5.3).

Gerbang yang masih perlu diselesaikan: gateway menyediakan sertifikat P-256 atau RSA yang kompatibel, lalu rantai sertifikat dan koneksi diverifikasi lagi pada API 24. Pengguna mengonfirmasi bahwa panel Nginx Proxy Manager di `https://proxy.bccdev.id` dikelola admin/tim BCC. Pemeriksaan baca-saja melalui SSH `cp-bcc` menemukan Nginx HTTP pada port 80 dan aplikasi Go pada loopback 18080, tanpa listener TLS 443. Konfigurasi gateway publik perlu ditangani pengelola BCC; konfigurasi VPS tidak diubah. Verifikasi sertifikat/hostname tetap memakai perilaku OkHttp/platform, dan minimum API 24 dipertahankan. Uji native gagal ini tidak dihitung sebagai kelulusan atau diganti dengan uji host.

Reproduksi setelah memasang APK remote dan APK penguji yang cocok:

```sh
adb -s emulator-5554 shell am instrument -w -r \
  -e verifyLiveReadOnly true \
  -e class com.example.wastenot.data.network.RemoteReadOnlyContractTest \
  com.example.wastenot.test/androidx.test.runner.AndroidJUnitRunner
```

Kasus ini hanya aktif ketika argumen tersebut diberikan. Ia melakukan GET metadata anonim dan mempertahankan kegagalan TLS sebagai kegagalan tes. Tidak membuat akun atau melakukan write pada deployment.

## Penerimaan yang masih diperlukan

Dashboard/statistik/progres harus memakai agregat server; satu halaman 20 catatan tidak boleh dianggap keseluruhan data. Pagination/filter, Panduan PUBLISHED dan AI publik masih mengikuti [peta integrasi](../../tasks/BACKEND_INTEGRATION.md). Lanjutkan penerimaan melalui screen remote, refresh/pergantian akun dan agregat dashboard/statistik setelah binding datanya tersedia, serta ulangi koneksi publik setelah pengelola BCC memperbaiki TLS. Jangan menyatakan mode remote siap dari tes repository lewat SSH ini.

Penerimaan seluruh screen/navbar/navigasi tetap berada pada [matriks Iteration 2](../../tasks/ITERATION_2_DESIGN.md). Hasil visual sebelumnya tetap berlaku pada source checkpoint masing-masing dan tidak dihitung ulang dalam angka transport ini.
