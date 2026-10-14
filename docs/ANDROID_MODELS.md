# Model data Android

Acuan: [PRD §8–9](WasteNot-Product-Design-Kit%20update/WasteNot/PRD.md),
[API §2,5–6](WasteNot-Product-Design-Kit%20update/WasteNot/API.md), issue [A02 #5](https://github.com/waste-not-app/waste-not-android/issues/5).

`model/` berisi aturan murni Kotlin; tidak menyimpan state Compose, context, atau credential.

- `WasteCategory`, `DisposalMethod`, dan `QuantityKind` memakai nama kanonik API. Label Indonesia dipetakan oleh UI melalui string resource, bukan nama enum terjemahan.
- `Quantity.Weight` dan `Quantity.Count` saling eksklusif. Factory memeriksa batas; `fromFields` mengembalikan null untuk respons/payload dengan kedua nilai, tanpa nilai, nilai salah jenis, atau di luar batas.
- `parseQuantity` menerima digit ASCII, trim luar, serta satu koma/titik desimal khusus kg. Tidak ada dukungan separator ribuan pada input. Dalam mode kg, `1.001` berarti 1,001 kg / 1001 g; grouping seperti `1.000,00` ditolak. Gram dan benda selalu bulat. Kg maksimal tiga desimal dan dikonversi tepat ke gram tanpa floating point.
- `QuantityInput.changeUnit` mengonversi g ↔ kg secara tepat. Perpindahan berat ↔ benda mengosongkan nilai, begitu pula pergantian satuan berat bila nilai sebelumnya invalid.
- Lapisan data menyediakan `Quantity.toRequestFields()` untuk quantity_kind/weight_g/item_count. Field jenis lain selalu JsonNull eksplisit agar PATCH pergantian jenis tidak mempertahankan jumlah lama. Kg hanya pilihan tampilan/parser; payload tetap integer gram. Repository write/editor belum terhubung.
- `WasteSummary` menerima nilai server; tidak menghitung agregat ulang dari daftar lokal. `handled` dan `avoided` masing-masing mempertahankan gram dan jumlah benda. Rate null adalah keadaan tanpa aktivitas penanganan.
- `WasteFormat` mengembalikan angka dan enum satuan terpisah agar UI memakai label resource. Detail berat memiliki maksimal tiga desimal kg; ringkasan dua; persentase satu desimal jika perlu. Null persentase tetap null untuk pesan tanpa denominator.
- Tanggal aktivitas adalah `LocalDate`, bukan timestamp UTC. Timestamp server diformat dengan zona akun eksplisit. `ActivityDates` menerima tanggal/instant server, tidak memakai jam perangkat secara diam-diam. Rentang 7/30 hari inklusif; validasi create mengizinkan hari ini dan 30 hari sebelumnya, sedangkan edit boleh mempertahankan tanggal lama yang sama.

`java.time` didukung pada minSdk 24 melalui core library desugaring. Tidak ada perubahan minSdk atau upgrade toolchain.

## Verifikasi

```bash
./gradlew :app:testDebugUnitTest --tests 'com.example.wastenot.model.*'
```

16 tes memeriksa batas, XOR unit, input invalid, round-trip g/kg, pembulatan tampilan, null rate, fixture server PRD §9.4, pergantian hari, zona akun, DST, dan rentang edit/create.

Layar editor/ringkasan belum memakai model ini. Wiring UI, serialisasi DTO, persetujuan fixture backend B01, serta pengujian perangkat merupakan pekerjaan lanjutan; tes model bukan bukti integrasi backend atau penerimaan seluruh layar.
