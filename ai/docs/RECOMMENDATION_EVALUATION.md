# Panduan pilot dan evaluasi rekomendasi

Pembaruan aktif: [pilot-policy-06, kondisi kosong dan audit background](CONDITIONAL_AND_BACKGROUND.md). UNKNOWN biasa boleh mendapat READY/CONDITIONAL; syarat yang belum diketahui muncul di conditions_to_verify. Bagian policy-05/evaluasi 40 kasus di bawah adalah perilaku/run sebelumnya; allow_conditional=false mempertahankan mode ketat tersebut. Suite aktif kini 44 kasus, dengan 40 kasus lama diuji dalam mode ketat dan empat kasus kondisi kosong. Hash report lama merujuk snapshot skenario sebelum perubahan, bukan byte file skenario aktif.

20 Oktober 2026, `pilot-policy-05`, katalog `demo-catalog-04`: **69 pilihan dan 69 panduan DRAFT**. Kandidat drinking-waste kini menghubungkan keempat kelas, termasuk HDPE, ke pilihan yang tetap dikonfirmasi. COCO tetap 58 kelas visual dengan cakupan lebih luas.

## Perubahan panduan

- HDPE mempunyai panduan sendiri, bukan alias PET. `condition.former_contents=FOOD_DRINK` dan `recycling_access=YES` diperlukan untuk RECYCLE. UNKNOWN meminta konteks, CHEMICAL memblokir aturan biasa, OTHER belum tercakup. Tidak ada REUSE otomatis dari kode resin.
- RECYCLE untuk pilot PET, botol kaca, kaleng, kardus dan baterai mensyaratkan `recycling_access=YES`. UNKNOWN meminta penerima; NO tidak ditebak menjadi TRASH. Ini berarti pengelola menerima **jenis dan kondisi yang sama**, termasuk jalur khusus baterai.
- Alasan/langkah per evidence menjelaskan kondisi dan tindakan yang relevan. Risiko/isi kimia tetap berlaku meskipun foto tampak bersih. Reduce hanya PREVENTION pilihan pengguna.
- Sumber HDPE menambahkan BPOM Tasikmalaya untuk identitas/peruntukan resin dan EPA untuk prinsip daur ulang. Baterai menambahkan DLH DKI sebagai contoh dropbox lokal. Batas wilayah tampil pada source_details, tanpa menjanjikan penerimaan nasional/penjemputan satu baterai. [Riset dan pemetaan klaim](PILOT_GUIDANCE_RESEARCH.md).
- Audit konfigurasi menolak nama/syarat enum yang salah, boolean berbentuk teks, sumber non-HTTPS dan evidence tanpa langkah sebelum panduan dilayani. Audit bawaan mencocokkan seluruh URL dengan registri, serta mengekspor klaim, syarat, langkah dan metadata sumber pilot untuk peninjau.

## Hasil skenario tetap

[recommendation-cases.json](../evaluations/recommendation-cases.json) berisi **40 kasus** yang ekspektasinya ditulis sebelum percobaan Gemini: kemasan diterima/akses unknown/NO; HDPE bekas pangan, isi unknown/kimia/lainnya; cangkir kotor biasa, syarat cuci kurang, kontaminasi dan pecah; baterai; pencegahan; panduan tidak ada/kedaluwarsa/belum dipublikasikan; serta enam respons model buruk.

| Evaluasi | Hasil | Panggilan generate sebenarnya |
|---|---:|---:|
| Fixture offline | 40/40 | 0 ke Gemini, 12 ke fixture |
| Gemini `gemini-3.5-flash-lite` | 40/40 | 12 ke Gemini |

Laporan [fixture](../evaluations/recommendation-fixture-20261020.json) dan [Gemini](../evaluations/recommendation-gemini-20261020.json) mencatat hash skenario/panduan, latensi, metode, alasan, sumber dan kegagalan per kasus. Pada masing-masing run, 22 kasus berhenti sebelum model dan enam kasus memakai **output adversarial fixture yang disuntikkan**, termasuk dalam run Gemini. Jadi 40/40 tidak berarti 40 panggilan Gemini atau keberhasilan Gemini menghadapi semua serangan prompt di dunia nyata. Enam kasus tersebut menguji validator: evidence palsu, metode terlarang, URL tambahan, JSON rusak, flag risiko berubah, dan instruksi bebas yang tidak boleh tampil.

Yang diperiksa: status, satu metode/scope, alasan fallback, missing_context, special_handling, batas panggilan provider, evidence mendukung metode, URL berasal dari evidence, langkah/sumber ada, serta instruksi model tidak bocor menjadi teks publik. Alasannya dirender dari evidence terverifikasi; Gemini memilih metode/evidence yang memenuhi syarat. Belum ada evaluasi preferensi ahli atau uji lapangan populasi pengguna.

```powershell
# Tanpa key, tanpa YOLO/GPU; juga dijalankan CI.
.\.venv-ai\Scripts\python.exe ai/scripts/evaluate_recommendations.py
# Memakai key/model di ai/.env; 12 logical generate calls untuk satu putaran saat ini.
.\.venv-ai\Scripts\python.exe ai/scripts/evaluate_recommendations.py --provider gemini --max-calls 12
# Uji variasi beberapa putaran dengan batas panggilan eksplisit.
.\.venv-ai\Scripts\python.exe ai/scripts/evaluate_recommendations.py --provider gemini --repeat 2 --max-calls 24
# HDPE langsung melalui CLI:
.\.venv-ai\Scripts\python.exe -m wastenot_ai.cli recommend --context ai/examples/context-hdpe.json --provider gemini --demo-guides
```

`--max-calls` membatasi generate logis, bukan jumlah percobaan HTTP internal ketika provider melakukan failover key. Preflight menolak anggaran yang kurang sebelum API dipanggil; kasus tidak dilewati diam-diam. Report percobaan baru default di `ai/artifacts/`, terpisah dari bukti run tersimpan.

Contoh context-glass/PET/battery/HDPE memakai akses YES sebagai **asumsi skenario demo**, bukan hasil verifikasi fasilitas nyata. Saat memakai benda sendiri, isi hanya sesuai penerima yang benar-benar diperiksa. Gunakan UNKNOWN untuk melihat NEEDS_CONTEXT.

## Handoff backend/frontend

Tambahkan `condition.former_contents` dengan enum FOOD_DRINK/CHEMICAL/OTHER/UNKNOWN; default UNKNOWN. Tampilkan field untuk HDPE dan gunakan label missing_context `condition.former_contents`. Mengganti isi sebelumnya, risiko, material, atau akses penerima membatalkan rekomendasi lama. Kondisi asli tidak diubah menjadi bersih hanya karena langkahnya menyarankan pencucian.

Ambil band/profile/mapping dari Python sesuai [kontrak HTTP](AI_HTTP_CONTRACT.md) dan [mutu deteksi](DETECTION_QUALITY.md). Jangan menduplikasi keputusan di Go atau frontend. Contoh request/response dan OpenAPI sudah diregenerasi. Panduan tetap DRAFT sampai penanggung jawab lokal meninjau klaim, fasilitas, syarat tindakan, sumber dan kasus negatif; reviewer/tanggal publikasi tidak diisi otomatis.

## Verifikasi implementasi

169 tes Python lulus; 32 artifact kontrak cocok dengan generator dan contoh divalidasi terhadap OpenAPI. Empat belas pemeriksaan browser baseline lulus, ditambah tiga pemeriksaan kandidat kaleng dan lima pemeriksaan HDPE (termasuk isi UNKNOWN/kimia dan pembatalan saran lama), tanpa error JavaScript. YOLO nyata juga diuji melalui dua adapter HTTP privat hingga keputusan fixture READY/RECYCLE. Wheel memuat modul/data/web yang diperlukan dan tidak memuat key, gambar atau bobot.

Kamera pengujian browser menggunakan perangkat simulasi Chromium atau unggah gambar validation yang eksplisit. Kamera fisik, Go/Android produksi, serta holdout kamera nyata belum dibuktikan oleh pemeriksaan ini. Screenshot dan laporan browser lokal tersedia di `ai/artifacts/`.
