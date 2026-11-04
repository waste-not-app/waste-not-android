# Kontrak rekomendasi empat metode

Pembaruan 20 Oktober 2026 mengikuti keputusan pengguna: Gemini memilih **tepat satu** dari `REDUCE`, `REUSE`, `RECYCLE`, `TRASH`, beserta alasan dan sumber panduan. Implementasi tunggal berada di Python, termasuk adapter HTTP privat. Go/Android mengintegrasikan [kontrak HTTP/OpenAPI](AI_HTTP_CONTRACT.md); tidak menyalin policy atau prompt ke Go.

Policy terbaru `pilot-policy-06`: penanganan khusus adalah peringatan tambahan. Cangkir kotor biasa bisa mendapat Reuse setelah dicuci; risiko yang belum jelas meminta informasi tambahan. REDUCE hanya untuk fokus PREVENTION yang dipilih pengguna. Dasar sumber dan evaluasinya dijelaskan pada [mutu panduan](GUIDE_QUALITY.md).

## Input konteks

Selain `object_key`, `category`, `condition`, `reuse_intention`, dan `risk_flag`:

| Field | Nilai | Default / arti |
|---|---|---|
| `recommendation_goal` | `AUTO`, `PREVENTION`, `HANDLING` | `AUTO`: memilih penanganan benda saat ini dari evidence yang sesuai |
| `recycling_access` | `YES`, `NO`, `UNKNOWN` | `UNKNOWN`: belum diperiksa; `NO` hanya setelah pengguna memeriksa akses penerima untuk benda tersebut |
| `condition.cleanable` | `YES`, `NO`, `UNKNOWN` | Apakah kotoran dapat dibersihkan sesuai petunjuk bahan/produk; default UNKNOWN |
| `condition.soiling` | `FOOD_DRINK`, `DUST`, `HAZARDOUS`, `UNKNOWN` | Jenis kotoran yang dikonfirmasi; default UNKNOWN |
| `risk_type` | `UNKNOWN`, `SHARP`, `CHEMICAL`, `BIOLOGICAL`, `ELECTRICAL`, `OTHER` | Default UNKNOWN; centang risk_flag tanpa jenis risiko meminta penjelasan |

`PREVENTION` hanya menawarkan evidence REDUCE; AUTO/HANDLING hanya REUSE/RECYCLE/TRASH. Kekurangan evidence penanganan tidak lagi diganti dengan pencegahan. Kondisi UNKNOWN tidak diubah menjadi fakta. Tidak adanya akses daur ulang tidak otomatis membolehkan Trash.

## Respons

Contoh respons lokal, diringkas:

```json
{
  "status": "READY",
  "recommended_method": "REDUCE",
  "recommendation_scope": "PREVENTION",
  "reason": "Untuk mencegah sampah berikutnya, kurangi pembelian yang tidak diperlukan dan pilih produk tahan lama atau kemasan lebih sedikit. Ini saran pencegahan, bukan pembuangan benda saat ini.",
  "special_handling": false,
  "caution": "Jangan anggap semua fasilitas menerima plastik ini.",
  "evidence_ids": ["pet_bottle-prevent-waste-v2"],
  "guide_refs": [{"guide_id": "pilot-pet-bottle", "version": 5}],
  "source_urls": ["https://www.epa.gov/climate-change/what-you-can-do-about-climate-change-waste"],
  "steps": ["Pada pembelian berikutnya, beli sesuai kebutuhan dan pertimbangkan produk tahan lama atau kemasan lebih sedikit."],
  "reason_code": null,
  "recommendation_version": "pilot-policy-05",
  "provider": "gemini",
  "prototype_only": true
}
```

| Label | Scope | Makna |
|---|---|---|
| `REDUCE` | `PREVENTION` | Pencegahan untuk penggunaan/pembelian berikutnya; foto bukan bukti berat sampah yang berhasil dihindari |
| `REUSE` | `HANDLING` | Gunakan kembali benda utuh dan layak sesuai evidence; peralatan kotor biasa yang bisa dibersihkan mendapat prasyarat mencuci dahulu |
| `RECYCLE` | `HANDLING` | Arahkan ke daur ulang sesuai jenis, kondisi, dan ketentuan fasilitas penerima |
| `TRASH` | `HANDLING` | Residu hanya jika ada evidence spesifik dan syaratnya terpenuhi; bukan default saat model ragu |

Tidak ada `alternative_method`. LLM memilih metode dan evidence; server memvalidasi keduanya, lalu merender alasan dari teks evidence terpilih. `source_urls` diambil dari evidence tersebut atau panduannya, bukan URL karangan model. Langkah mengikuti metode terpilih, sehingga REDUCE tidak menampilkan langkah pembuangan dari guide botol. Prosa bebas model tidak diteruskan menjadi instruksi penanganan.

`READY` selalu memiliki satu metode, scope sesuai, evidence dan sumber. `special_handling=true` **tetap boleh READY dengan label yang jelas**: kaca pecah → TRASH dengan pengemasan pelindung; baterai/elektronik/pisau/gunting → RECYCLE melalui pengelola khusus. Tampilan menempatkan peringatan di bawah label. Status SPECIAL_HANDLING dihapus dari kontrak aktif.

Pada objek berisiko, hanya evidence dengan `risk_compatible=true` yang boleh dipilih. Evidence REUSE biasa tidak lolos. Sumber/langkah harus mengikuti metode terpilih: kaca pecah tidak menampilkan langkah memakai kembali benda yang utuh. Flag risiko tidak boleh dihapus model. Risiko UNKNOWN yang dicentang meminta risk_type; kontaminasi kimia/biologis tidak memakai aturan cuci/residu biasa. AUTO/HANDLING tidak berubah menjadi PREVENTION.

`NEEDS_CONTEXT` memiliki recommended_method/recommendation_scope null dan `missing_context` berisi isian yang perlu dijawab. Contoh: `["risk_type"]`, `["condition.soiling","condition.cleanable"]`. Gemini belum dipanggil. Server memilih isian yang relevan dari syarat evidence yang masih mungkin dipenuhi, bukan meminta semua field yang kosong. Pada READY, `source_details` menyertai source_urls dengan judul, penerbit, wilayah, checked_at dan scope_note.

`UNAVAILABLE` memakai metode/scope null hanya ketika panduan/layanan/konteks yang memadai tidak tersedia atau keluaran model gagal validasi. Error tetap ditampilkan jujur; sistem tidak merekayasa label seolah Gemini berhasil. Caution risiko tetap dipertahankan saat terjadi kegagalan.

## Mencoba keempat hasil

Jalankan `ai/demo-camera.bat` dari root repo. Pilih Gemini untuk model sungguhan atau Simulasi untuk uji offline. Semua contoh tetap harus sesuai kondisi benda yang diperiksa.

| Hasil yang dicoba | Pilihan objek dan konteks |
|---|---|
| Recycle | Botol kaca, bersih, utuh, niat pakai ulang Tidak; fokus Otomatis |
| Reduce | Botol apa pun yang aman; fokus Pencegahan berikutnya |
| Reuse | Buku kertas, bersih, utuh, masih layak Ya, niat pakai ulang Ya; fokus Otomatis |
| Reuse setelah dicuci | Cangkir keramik/kaca/logam, Kotor, Utuh, layak Ya, ingin pakai ulang Ya; jenis kotoran sisa makanan/minuman atau debu, cleanable Ya, tanpa risiko tambahan |
| Trash | Sikat gigi **plastik manual**, masih layak Tidak, akses daur ulang Sudah diperiksa tidak ada; fokus Otomatis |
| Trash + peringatan | Gelas kaca / botol kaca / cangkir keramik, keutuhan Rusak/pecah; fokus Otomatis |
| Recycle + peringatan | Baterai, laptop, pisau atau gunting; fokus Otomatis, periksa rute pengelola yang menerima benda tersebut |

Katalog manual tersedia untuk menyiapkan skenario tanpa mengulang kamera. Label YOLO `toothbrush` belum membuktikan plastik manual: pengguna wajib mengonfirmasi bahan dan tidak adanya baterai/listrik. Sikat gigi elektrik harus mengikuti penanganan elektronik khusus. Panduan Trash spesifik sikat plastik manual; tidak digeneralisasi ke semua barang campuran.

Konteks CLI: [Reduce](../examples/context-reduce.json), [Reuse](../examples/context-reuse.json), [Recycle](../examples/context-glass.json), [Trash](../examples/context-trash.json), [kaca pecah](../examples/context-broken-glass.json), [baterai](../examples/context-battery.json). Gunakan `recommend --context <path> --provider gemini --demo-guides` dengan interpreter venv seperti pada [README](../README.md#gemini-api).

## Integrasi Go dan Android

1. Go memakai schema empat label dan meneruskan konteks serta snapshot terpilih ke modul Python keputusan. Python menyaring evidence, menghitung risiko dan mengembalikan NEEDS_CONTEXT sebelum Gemini bila diperlukan. Go menyimpan snapshot scope/policy/sumber dan memegang sesi, autentikasi, idempotensi serta ownership publik.
2. Android mengirim konteks yang dikonfirmasi, memetakan enum uppercase ke label Reduce/Reuse/Recycle/Trash, menampilkan **satu label + alasan + tautan sumber**, lalu langkah/caution. special_handling=true menambahkan peringatan tanpa menghilangkan label. NEEDS_CONTEXT menunjukkan isian yang diperlukan; source_details menyediakan identitas/cakupan sumber. Saat konteks/goal/access berubah, batalkan hasil lama.
3. Rekomendasi tidak otomatis menyimpan aktivitas. REDUCE baru boleh dicatat ketika tindakan pencegahan benar-benar dilakukan dan jumlah yang dihindari diisi pengguna. Foto benda yang sudah ada tidak digunakan sebagai bukti jumlah Reduce. Jalur pencatatan Reduce tetap manual sampai backend mendukung validasi provenance pencegahan secara eksplisit.

## Sumber draf

- [EPA: What You Can Do About Climate Change — Waste](https://www.epa.gov/climate-change/what-you-can-do-about-climate-change-waste): prinsip pencegahan, penggunaan ulang dan daur ulang.
- [EPA: Common Recyclables](https://www.epa.gov/recycle/how-do-i-recycle-common-recyclables): panduan bahan yang dapat didaur ulang.
- [Cornwall Council: Toothbrushes](https://www.cornwall.gov.uk/rubbish-recycling-and-waste/recycling/recycling-items/toothbrushes/): contoh sikat plastik yang tidak masuk pengumpulan daur ulang biasa, sebagai sumber spesifik draf Trash.
- [Cornwall Council: Broken glass](https://www.cornwall.gov.uk/rubbish-recycling-and-waste/recycling/recycling-items/broken-glass/): jalur residu untuk pecahan kaca dengan pengemasan pelindung dan label.
- [Islington Council: Household rubbish](https://www.islington.gov.uk/recycling-and-rubbish/recycling/what-we-pick-up-as-household-rubbish): peralatan keramik pada jalur residu rumah tangga.
- [EPA: Used Household Batteries](https://www.epa.gov/recycle/used-household-batteries) dan [Electronics Donation and Recycling](https://www.epa.gov/recycle/electronics-donation-and-recycling): pengelolaan melalui pengumpul khusus, bukan tong daur ulang biasa.
- [Wandsworth Council: Kitchen knives](https://www.wandsworth.gov.uk/rubbish-and-recycling/recycle-and-reuse/waste-and-recycling-a-z/kitchen-knives/) dan [Auckland Council: Recycling Centre](https://www.aucklandcouncil.govt.nz/en/rubbish-recycling/get-rid-unwanted-items/organisation/331.html): contoh penerima khusus pisau/gunting.

Sumber diperiksa 20 Oktober 2026. Referensi luar Indonesia memberi prinsip/contoh yurisdiksi, bukan jaminan penerimaan fasilitas lokal. Seluruh panduan tetap DRAFT sampai ditinjau penanggung jawab lokal; tidak ada reviewer atau status publikasi yang direkayasa.

Policy-05 menambah former_contents untuk HDPE dan konfirmasi penerima RECYCLE pilot. Lihat [hasil evaluasi 40 skenario](RECOMMENDATION_EVALUATION.md). UNKNOWN tetap meminta konteks dan tidak diganti Reduce.

Policy-06: field biasa yang belum diketahui dapat READY dengan recommendation_basis=CONDITIONAL dan conditions_to_verify. Tidak ada asumsi otomatis bersih/utuh/layak/penerima. allow_conditional=false mempertahankan mode ketat NEEDS_CONTEXT. Risiko dan isi HDPE tetap memiliki safety gate. Lihat [kontrak bersyarat](CONDITIONAL_AND_BACKGROUND.md).
