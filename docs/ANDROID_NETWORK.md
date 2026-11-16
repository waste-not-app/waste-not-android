# Lapisan jaringan Android

Acuan: [API v1.2](WasteNot-Product-Design-Kit%20update/WasteNot/API.md),
[arsitektur §8](WasteNot-Product-Design-Kit%20update/WasteNot/ARCHITECTURE.md), [A03 #6](https://github.com/waste-not-app/waste-not-android/issues/6).

`WasteNotNetwork` menyediakan satu OkHttp client, satu converter kotlinx.serialization, API Retrofit, dan executor hasil terarah. `CoreRepository` memetakan DTO snake_case ke model. Base URL kosong menghasilkan `NOT_CONFIGURED`, bukan alamat produksi buatan.

- Bearer dibatasi pada scheme, host, port, dan prefix path API. Header Authorization/Cookie yang diberikan pemanggil dihapus pada request lain. Login/register/refresh dan kedua endpoint pemulihan password tidak menerima bearer lama. Redirect otomatis dimatikan; source_url dibuka melalui alur tautan eksternal, bukan endpoint terautentikasi.
- Tidak ada HTTP body/header logger. Diagnostic opsional hanya menerima status, durasi, dan request ID berkarakter terbatas; tidak menerima URL, exception message, password, token, atau payload. Sink log belum dihubungkan ke aplikasi.
- Repository mengembalikan sukses dengan pagination atau `ApiFailure` yang mempertahankan status, code, field errors, request ID dan deadline retry. Pesan teknis server tidak diteruskan sebagai pesan umum aplikasi. Error field 409/422 tersedia untuk pemetaan resource/form; respons 5xx memakai jenis generik.
- `Retry-After` 429 berupa detik atau HTTP date mengunci request API berikutnya sampai deadline. Hasil langsung menyatakan rate limit sehingga UI dapat menampilkan waktu tunggu. Tidak ada retry loop atau write queue.
- Timeout, gangguan jaringan, respons malformed dan cancellation dibedakan. Cancellation diteruskan kepada pemanggil. SessionRepository menangani refresh 401 terserialisasi; executor HTTP tidak mengulang write sendiri.
- Metadata/capability adalah data tampilan, bukan kontrol authorization. Capability yang tidak dikirim/nonaktif menghasilkan false. Respons dengan enum, batas, quantity XOR, zona, tanggal atau pagination invalid ditolak sebagai malformed.
- Body POST/PUT/PATCH/DELETE ditandai one-shot pada setiap pemanggilan, termasuk DELETE kosong, agar OkHttp tidak replay 503/421 secara internal. Retry yang diperlukan harus membuat panggilan eksplisit di repository dengan payload/ID yang diketahui.

## Fixture untuk B01 — usulan, belum kontrak backend yang disepakati

Fixture tersimpan bersama tes agar backend dapat memakai file yang sama:

- [`metadata.json`](../app/src/test/resources/contracts/metadata.json)
- [`waste-logs-page.json`](../app/src/test/resources/contracts/waste-logs-page.json)
- [`validation-error.json`](../app/src/test/resources/contracts/validation-error.json)

API.md menetapkan isi metadata tetapi belum bentuk JSON rinci kategori/metode dan key batas. Implementasi Android mengusulkan array enum string dan objek `limits` seperti fixture. `weight_g`, `item_count`, `name_length`, dan `password_length` memiliki `{min,max}`; batas lain berupa integer. Nilainya mengikuti PRD, tidak mengubah aturan produk. Backend B01 harus menyepakati atau mengganti shape fixture/DTO ini bersama-sama sebelum integrasi nyata. Jangan mengklaim endpoint/fixture ini sudah deployed atau disetujui tim backend.

Kategori/metode yang dikirim adalah pilihan tersedia yang dikenali aplikasi; tambahan enum baru memerlukan pembaruan aplikasi. Batas metadata hanya boleh memperketat baseline PRD. UI harus memakai batas server saat wiring form; parser model baseline tetap memeriksa batas kanonik maksimum.

## Pengujian

```bash
./gradlew :app:testDebugUnitTest --tests 'com.example.wastenot.data.network.*'
```

17 tes jaringan menguji pemetaan data/pagination/error, metadata, respons invalid, pembatasan credential, redirect, deadline 429, timeout/cancellation, dan larangan replay write. Regresi 503 awalnya menghasilkan 201 karena POST dikirim ulang; setelah pengaman one-shot, POST/PUT/PATCH/DELETE masing-masing hanya terkirim satu kali. Tes timeout memakai client khusus satu detik; tes cancellation memakai batas normal dan server baru agar tidak terpengaruh sisa koneksi timeout. Fixture menggunakan data simulasi dan credential palsu. Ini bukan bukti login/backend nyata atau penerimaan UI.

Application/AppContainer A04 membuat satu network client bersama penyimpanan sesi dan repository refresh terserialisasi. CoreRepository produksi memakai guard sesi dan credential request yang dibekukan; layar belum memakai repository ini. Kontrak fixture masih perlu disepakati dengan backend.

## Pemulihan dan penghapusan akun

`RemoteAccountManagement` memakai `POST auth/password-reset` dan `POST auth/password-reset/confirm` tanpa credential sesi. Respons request 202 merupakan acknowledgement bersyarat, bukan bukti pengiriman email. Kode ditempel ke aplikasi, bukan tautan URL. Password tidak di-trim dan DTO/state meredaksi isian rahasia dari `toString`.

`DELETE me` membawa password pada body JSON dan credential akun yang dibekukan. `403 WRONG_PASSWORD` bukan expiry sesi: data lokal tetap tersedia. `204` mencabut sesi dan membersihkan Room/outbox/receipt/snapshot/identitas, draf serta cache scan hanya milik akun tersebut. Logout biasa tetap mempertahankan antrean sesuai OFFLINE_SYNC.

`503 ACCOUNT_DELETION_PENDING` adalah hasil khusus setelah penghapusan akun server commit: aplikasi membersihkan akun lokal dan keluar dengan pemberitahuan pembersihan lampiran server masih tertunda. Error 503 biasa atau gangguan jaringan tidak diperlakukan sebagai persetujuan penghapusan. Kegagalan pembersihan perangkat memiliki pemberitahuan tersendiri; aplikasi tidak menyatakan seluruh data fisik atau backup sudah hilang.
