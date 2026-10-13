# Lapisan jaringan Android

Acuan: [API v1.2](WasteNot-Product-Design-Kit%20update/WasteNot/API.md),
[arsitektur §8](WasteNot-Product-Design-Kit%20update/WasteNot/ARCHITECTURE.md), [A03 #6](https://github.com/waste-not-app/waste-not-android/issues/6).

`WasteNotNetwork` menyediakan satu OkHttp client, satu converter kotlinx.serialization, API Retrofit, dan executor hasil terarah. `CoreRepository` memetakan DTO snake_case ke model. Base URL kosong menghasilkan `NOT_CONFIGURED`, bukan alamat produksi buatan.

- Bearer dibatasi pada scheme, host, port, dan prefix path API. Header Authorization/Cookie yang diberikan pemanggil dihapus pada request lain. Login/register/refresh tidak menerima bearer lama. Redirect otomatis dimatikan; source_url dibuka melalui alur tautan eksternal, bukan endpoint terautentikasi.
- Tidak ada HTTP body/header logger. Diagnostic opsional hanya menerima status, durasi, dan request ID berkarakter terbatas; tidak menerima URL, exception message, password, token, atau payload. Sink log belum dihubungkan ke aplikasi.
- Repository mengembalikan sukses dengan pagination atau `ApiFailure` yang mempertahankan status, code, field errors, request ID dan deadline retry. Pesan teknis server tidak diteruskan sebagai pesan umum aplikasi. Error field 409/422 tersedia untuk pemetaan resource/form; respons 5xx memakai jenis generik.
- `Retry-After` 429 berupa detik atau HTTP date mengunci request API berikutnya sampai deadline. Hasil langsung menyatakan rate limit sehingga UI dapat menampilkan waktu tunggu. Tidak ada retry loop atau write queue.
- Timeout, gangguan jaringan, respons malformed dan cancellation dibedakan. Cancellation diteruskan kepada pemanggil. Refresh 401 belum dipasang; ini cakupan A04.
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

15 tes MockWebServer menguji pemetaan data/pagination/error, metadata, respons invalid, pembatasan credential, redirect, deadline 429, timeout/cancellation, dan larangan replay write. Regresi 503 awalnya menghasilkan 201 karena POST dikirim ulang; setelah pengaman one-shot, POST/PUT/PATCH/DELETE masing-masing hanya terkirim satu kali. Fixture menggunakan data simulasi dan credential palsu. Ini bukan bukti login/backend nyata atau penerimaan UI.

Lapisan ini belum dibuat oleh AppContainer dan belum dipakai screen. A04 memasang penyimpanan sesi serta refresh terserialisasi; wiring aplikasi dilakukan setelah dependensi tersebut tersedia.
