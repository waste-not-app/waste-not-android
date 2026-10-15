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

Model/aturan dan parser kini dipakai editor, detail serta ringkasan sumber contoh. Mapper request menjaga quantity eksklusif, dan dataset PRD 9.4 diperiksa unit serta layar. Bukti penerimaan client ada di [laporan QA](ITERATION_1_QA.md); backend write nyata tetap integrasi berikutnya.

## Lanjutan pemetaan payload

Branch `codex/android-quantity-payload-a02` menambahkan `Quantity.toRequestFields()` pada lapisan data. Mapper menerima Quantity yang telah lolos factory/parser dan menerbitkan quantity_kind, weight_g, item_count. WEIGHT berisi integer gram dan item_count=null; COUNT berisi integer benda dan weight_g=null. Null eksplisit diperlukan agar PATCH yang mengganti jenis jumlah tidak mempertahankan nilai lama di server.

Tes request retry sesi sekarang memakai mapper ini pada body HTTP melalui MockWebServer. Lima tes baru memeriksa batas gram/count, g/kg koma/titik ke payload identik, pergantian jenis pada PATCH, dan null eksplisit meskipun opsi serializer menghilangkan null properti biasa. Kelimanya lulus pada jalur native tanpa rewrite HotSwan; seluruh 78 unit test debug dan lint debug lulus. Request fixture yang diulang setelah 401 mempertahankan body serta client_request_id identik.

Pada PR #93 editor belum terhubung; branch lanjutan di bawah menyediakan create/edit dengan sumber contoh yang diizinkan pengguna. Pengujian PATCH memeriksa penggabungan field JSON sesuai kontrak, bukan update backend nyata.

## Batas dan tindak lanjut

Branch `codex/android-auth-navigation` menghubungkan QuantityInput/parser ke EditorDraft yang dipakai create/edit, serta formatter ke detail/beranda/statistik. DemoActivityStore menyediakan lima record PRD 9.4 dan demoSummary memisahkan handled/avoided, gram/benda serta denominator aktivitas. Live summary tidak dihitung dari halaman list parsial. Unit test memeriksa dataset/edit/idempotensi/draf; tes layar memeriksa angka 650 g, 1,4 kg, 2 benda serta denominator 1/2 dan 2/4.

DTO respons A03 dan mapper quantity request tersedia. Kontrak mengikuti rencana API; penggantian sumber dummy ke backend akan diverifikasi pada integrasi berikutnya. Tes domain/payload tidak membuktikan rendering atau TalkBack.
