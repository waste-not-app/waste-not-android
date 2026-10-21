# Mutu panduan dan evaluasi konteks

Pembaruan aktif: [pilot-policy-06, kondisi kosong dan audit background](CONDITIONAL_AND_BACKGROUND.md). UNKNOWN biasa boleh mendapat READY/CONDITIONAL; syarat yang belum diketahui muncul di conditions_to_verify. Bagian policy-05/evaluasi 40 kasus di bawah adalah perilaku/run sebelumnya; allow_conditional=false mempertahankan mode ketat tersebut. Suite aktif kini 44 kasus, dengan 40 kasus lama diuji dalam mode ketat dan empat kasus kondisi kosong. Hash report lama merujuk snapshot skenario sebelum perubahan, bukan byte file skenario aktif.

Pembaruan 20 Oktober 2026, `pilot-policy-05`. Katalog kini 69 benda/material; memperbanyak nama benda belum membuktikan mutu rekomendasi. Perubahan ini memperbaiki syarat, sumber, dan kasus uji, khususnya cangkir kotor yang sebelumnya mendapat Reduce.

## Keputusan yang diperbaiki

| Konteks | Hasil yang diharapkan | Dasar |
|---|---|---|
| Cangkir utuh, layak, ingin dipakai lagi, kotor sisa minuman/debu dan dikonfirmasi bisa dibersihkan | Reuse **setelah dicuci** | Panduan peralatan makan/minum guna ulang dan langkah pencucian |
| Cangkir yang sama, tetapi jenis kotoran/kemampuan dibersihkan belum diketahui | NEEDS_CONTEXT dengan isian yang perlu dilengkapi | Tidak menebak jenis kotoran atau keamanan |
| Risiko khusus dicentang tetapi jenisnya belum jelas | NEEDS_CONTEXT, meminta risk_type; belum memanggil Gemini | Centang risiko bukan bukti semua benda harus dibuang atau dikurangi pembeliannya |
| Ada kontaminasi kimia/biologis yang belum tercakup panduan | UNAVAILABLE dengan peringatan | Jangan memakai aturan cuci biasa/residu umum untuk risiko yang berbeda |
| Kaca/keramik pecah tanpa kontaminasi tambahan | Trash, pengemasan pelindung dan sumber | Instruksi memakai ulang tidak ikut ditampilkan |
| Baterai/elektronik | Recycle melalui pengelola khusus | Bukan tong daur ulang rumah tangga |
| Fokus Pencegahan berikutnya dipilih pengguna | Reduce, scope PREVENTION | Bukan fallback ketika penanganan saat ini belum diketahui |

Saran Reuse tidak mengubah input DIRTY menjadi CLEAN. Status benda sebelum tindakan dan langkah yang perlu dilakukan tetap berbeda. `cleanable=YES` adalah konfirmasi pengguna, bukan kesimpulan dari foto.

## Isi minimum setiap aturan

1. Sebutkan benda/material dan tujuan tindakannya; hindari aturan terlalu umum untuk semua plastik atau semua benda kotor.
2. Tulis syarat yang dapat dijawab pengguna, termasuk pengecualian. Jangan mengubah UNKNOWN menjadi YES.
3. Sediakan alasan yang menjelaskan **mengapa kondisi tersebut memenuhi metode**, lalu langkah/prasyarat yang sesuai metode itu.
4. Hubungkan sumber primer dengan klaim yang benar-benar didukung. Referensi penggunaan ulang tidak membuktikan semua residu diterima bank sampah.
5. Catat penerbit, wilayah, tanggal pemeriksaan referensi dan batas cakupannya. Tanggal pemeriksaan tautan bukan tanggal review ahli atau persetujuan lokal.
6. Tambahkan kasus positif, kasus negatif yang mirip, kondisi tidak diketahui, dan kontaminasi tambahan sebelum memperluas aturan.

`source_registry.json` menyimpan metadata sumber. `source_details` pada respons memakai metadata itu sehingga demo menampilkan judul/penerbit/wilayah, bukan hanya URL. Semua URL evidence bawaan dicocokkan dengan registri dalam tes. Sumber kustom yang belum tercatat diberi label metadata belum tersedia, bukan identitas penerbit yang direkayasa.

## Dasar sumber untuk peralatan guna ulang

[EPA Food Service Ware](https://www.epa.gov/greenerproducts/identifying-sustainable-food-service-and-food-service-ware) menjelaskan penggunaan peralatan yang mendukung siklus pengumpulan, pencucian dan penggunaan kembali, termasuk bahan tahan lama. Panduan itu mendukung tindakan **Reuse setelah pencucian**; tidak menyatakan foto cukup untuk menetapkan kebersihan atau keamanan barang.

[Pemkab Banjarnegara](https://banjarnegarakab.go.id/2026/06/11/banjarnegara-terapkan-aturan-baru-kelola-sampah-wajib-dari-hulu/) mendorong penggantian plastik sekali pakai dengan peralatan makan/minum guna ulang. Ini menambah rujukan Indonesia tentang perilaku penggunaan ulang, bukan klaim bahwa aturan penerimaan fasilitas sama di seluruh Indonesia.

[EPA Household Hazardous Waste](https://www.epa.gov/hw/household-hazardous-waste-hhw) menekankan perhatian pada residu bahan berbahaya, label produk dan layanan pengelolaan setempat. Karena itu, kontaminasi kimia tidak diperlakukan sebagai noda minuman yang cukup dicuci biasa.

## Reproduksi dan pengujian

```powershell
# Cangkir kotor biasa, fixture tanpa biaya API:
.\.venv-ai\Scripts\python.exe -m wastenot_ai.cli recommend --context ai/examples/context-dirty-mug.json --provider fixture --demo-guides
# Gemini sungguhan menggunakan key/model di ai/.env:
.\.venv-ai\Scripts\python.exe -m wastenot_ai.cli recommend --context ai/examples/context-dirty-mug.json --provider gemini --demo-guides
# Kasus mutu panduan dan audit seluruh evidence/sumber bawaan:
.\.venv-ai\Scripts\python.exe -m pytest ai/tests/test_guide_quality.py -q -p no:cacheprovider
```

Tes mencakup beberapa bahan/peralatan, sisa makanan/minuman versus debu, informasi pencucian yang belum diketahui, kontaminasi berbahaya dan risiko ambigu. Hasil fixture menguji kontrak/rule retrieval; evaluasi Gemini live tetap terpisah. Jangan melaporkan hasil tes ini sebagai akurasi klasifikasi dunia nyata.

Semua panduan masih DRAFT. Sebelum dipakai pada pilot pengguna, penanggung jawab lokal perlu meninjau isi, fasilitas yang menerima, syarat tindakan, dan kasus uji; baru mengisi reviewer/reviewed_at/valid_until yang sebenarnya. Tidak ada status PUBLISHED atau reviewer ahli yang dibuat otomatis oleh pembaruan ini.

Perbaikan pilot lanjutan: HDPE tersendiri, isi sebelumnya, penerima wajib dikonfirmasi, sumber BPOM/DLH lokal dan audit evidence. Hasil serta reproduksi fixture/Gemini tersedia di [RECOMMENDATION_EVALUATION](RECOMMENDATION_EVALUATION.md); pemetaan klaim di [PILOT_GUIDANCE_RESEARCH](PILOT_GUIDANCE_RESEARCH.md).
