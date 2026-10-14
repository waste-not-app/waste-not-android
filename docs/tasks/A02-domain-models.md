# A02 — Model, parser jumlah, dan tanggal

Issue [#5](https://github.com/waste-not-app/waste-not-android/issues/5) · PR [#89](https://github.com/waste-not-app/waste-not-android/pull/89) · status In review.

## Yang dikerjakan

- Enum kanonik tujuh kategori, empat metode, dan jenis WEIGHT/COUNT; label terjemahan dipisahkan dari key API.
- Model quantity eksklusif dengan batas gram/count; parser desimal tepat tanpa floating point atau pembulatan input.
- Pergantian g ↔ kg mempertahankan nilai tepat; perpindahan berat ↔ benda mengosongkan input.
- Model summary server memisahkan handled/avoided serta gram/benda. Formatter menjaga rate null, angka Indonesia, dan unit terpisah.
- Tanggal aktivitas tetap LocalDate; timestamp memakai zona akun eksplisit. Rentang inklusif serta aturan tanggal create/edit mengikuti PRD. Desugaring mendukung java.time pada minSdk 24.

Commit `d9060f2`. Rincian aturan dan contoh ada di [ANDROID_MODELS.md](../ANDROID_MODELS.md).

## Bukti

16 tes domain lulus: batas/overflow, input ambigu/invalid, presisi kg, XOR unit, round-trip satuan, dataset PRD §9.4, rate tanpa denominator, zona akun/DST, dan rentang tanggal. Seluruh 19 tes pada PR model serta lint debug lulus hook.

CI head `d9060f2`: [push](https://github.com/waste-not-app/waste-not-android/actions/runs/38458547435), [PR](https://github.com/waste-not-app/waste-not-android/actions/runs/38458561395).

## Checklist issue

Lima butir implementasi model/aturan dan penerimaan parser 250 g ↔ 0,25 kg telah ditandai. Penerimaan request/UI dan tampilan dataset tetap terbuka sampai model dipakai layar terkait.

## Lanjutan pemetaan payload

Branch `codex/android-quantity-payload-a02` menambahkan `Quantity.toRequestFields()` pada lapisan data. Mapper menerima Quantity yang telah lolos factory/parser dan menerbitkan quantity_kind, weight_g, item_count. WEIGHT berisi integer gram dan item_count=null; COUNT berisi integer benda dan weight_g=null. Null eksplisit diperlukan agar PATCH yang mengganti jenis jumlah tidak mempertahankan nilai lama di server.

Tes request retry sesi sekarang memakai mapper ini pada body HTTP melalui MockWebServer. Lima tes baru memeriksa batas gram/count, g/kg koma/titik ke payload identik, pergantian jenis pada PATCH, dan null eksplisit meskipun opsi serializer menghilangkan null properti biasa. Kelimanya lulus pada jalur native tanpa rewrite HotSwan; seluruh 78 unit test debug dan lint debug lulus. Request fixture yang diulang setelah 401 mempertahankan body serta client_request_id identik.

Repository write dan editor create/edit belum terhubung. Pengujian PATCH di sini memeriksa penggabungan field JSON sesuai kontrak, bukan update backend nyata. Checklist gabungan request/UI tetap terbuka. Sesuai arahan terbaru, layar boleh memakai sumber data dummy yang ditandai sambil menunggu implementasi backend.

## Batas dan tindak lanjut

Formatter/QuantityInput belum dihubungkan ke editor atau statistik. DTO respons A03 dan mapper quantity request tersedia. Kontrak mengikuti rencana API; penggantian sumber dummy ke backend akan diverifikasi pada integrasi berikutnya. Tes domain/payload tidak membuktikan rendering atau TalkBack.
