# A03 — HTTP, DTO, metadata, dan error API

Issue [#6](https://github.com/waste-not-app/waste-not-android/issues/6) · PR [#90](https://github.com/waste-not-app/waste-not-android/pull/90) · status In review.

## Yang dikerjakan

- Satu client OkHttp/Retrofit dan converter JSON; repository memetakan DTO snake_case ke model.
- Metadata dengan enum, batas, dan capability; endpoint kosong menjadi kegagalan konfigurasi terarah. Shape metadata rinci merupakan usulan B01 yang didokumentasikan, bukan kontrak yang sudah disetujui backend.
- Envelope sukses/pagination serta error/status/field/request ID dipetakan ke ApiResult. Respons malformed ditolak tanpa meneruskan detail teknis server.
- Bearer dibatasi origin/prefix API; credential caller dihapus pada request eksternal; auth publik tidak membawa bearer lama. Redirect otomatis dimatikan.
- Deadline Retry-After 429, timeout, cancellation, dan diagnostic tanpa URL/body/credential. Write one-shot mencegah replay internal POST/PUT/PATCH/DELETE.
- Fixture JSON bersama dan batas integrasi tersedia di [ANDROID_NETWORK.md](../ANDROID_NETWORK.md).

Commit `2dd1adb` dan `32227cb`.

## Bukti dan perbaikan CI

Tes awal membuktikan POST 503 dengan Retry-After: 0 terkirim ulang oleh library dan mendapat 201. Pengaman one-shot memperbaikinya; keempat metode write masing-masing terkirim satu kali.

CI berikutnya menemukan interferensi tes gabungan timeout/cancellation pada koneksi satu detik. Kedua kasus dipisah: timeout memakai client khusus dan cancellation memakai batas normal/server baru. Regresi dengan dua CPU serta seluruh **36 unit test** (17 jaringan, 16 domain, 3 starter) dan lint debug lulus hook.

CI head `32227cb`: [PR](https://github.com/waste-not-app/waste-not-android/actions/runs/38497998952) dan [push](https://github.com/waste-not-app/waste-not-android/actions/runs/38497993789) sama-sama berhasil. Masing-masing run menyimpan dua artifact: APK dan laporan pemeriksaan.

Tiga APK lokal berhasil dibangun; 13 pemeriksaan environment/paket dan lima fixture pengemasan release lulus. Install/startup pada API 36 dicatat di A01, dengan batas bukti perangkat yang sama.

## Checklist issue dan tindak lanjut

Implementasi client dan penerimaan fixture mempunyai bukti tes; issue tetap In review karena belum terintegrasi pada AppContainer/screen dan kesepakatan backend B01 masih terbuka. Refresh 401 terserialisasi serta credential storage dikerjakan A04. Tidak ada klaim login, backend deployed, atau penerimaan layar dari tes MockWebServer.
