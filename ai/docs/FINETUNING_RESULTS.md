# Hasil fine-tuning pertama — 20 Oktober 2026

**YOLO11n berhasil dilatih pada Drinking Waste Classification v2** untuk
kaleng aluminium, botol kaca, botol HDPE, dan botol PET. Ini kandidat eksperimen
botol/kaleng; model COCO tetap menjadi default demo dengan cakupan luas.
Sumber/lisensi dan reproduksi: [fine-tuning](FINE_TUNING.md),
[riset dataset](DATASET_RESEARCH.md), [notices](DATASET_NOTICES.md).

## Training dan artifact

- 4.572 gambar setelah audit; train 3.462, validation 681, test 429.
- Test berisi 503 instance: kaleng 132, kaca 151, HDPE 34, PET 186.
- Pretrained YOLO11n, input 640, AdamW, batch 16, workers 4, AMP, seed 42.
- Selesai **30 epoch**, early stopping patience 12; checkpoint terbaik dari
  **epoch 18**, dipilih memakai validation. Training sekitar 28,2 menit.
- RTX 4070 Laptop 8 GB; Python 3.13.1, torch 2.9.1+cu128, torchvision
  0.24.1+cu128, Ultralytics 8.4.183.
- Bobot lokal: `ai/artifacts/training/drinking-waste-v2-yolo11n/weights/best.pt`
  (5.491.866 byte).
- SHA-256: `af1446f3333806b0aba3b4618846fe8f95cf5ab3269e2730629b090702498bcf`.
- Model version adapter: `candidate-af1446f33338`; mapping pada evaluasi awal
  `drinking-waste-reviewed-01`. Profil keputusan terbaru menggunakan
  `drinking-waste-reviewed-02+dw-val-01`, dijelaskan di bagian pembaruan bawah.

Dataset dan bobot tidak masuk Git. Laporan mentah lokal berada pada
`ai/artifacts/training/drinking-waste-v2-yolo11n/evaluation.json`, kurva pada
`results.csv`, dan confusion matrix/PR curves pada subfolder `test`.

## Evaluasi test empat kelas

Agregat Ultralytics: precision **0,875**, recall **0,864**, mAP@0.5 **0,926**,
mAP@0.5:0.95 **0,735**. P/R ini mengikuti titik kurva F1 yang dipilih evaluator,
bukan jaminan hasil pada threshold demo 0,50/0,70 atau conf 0,25.

| Kelas | Precision | Recall | mAP@0.5 | mAP@0.5:0.95 |
|---|---:|---:|---:|---:|
| Kaleng aluminium | 0,924 | 0,649 | 0,857 | 0,527 |
| Botol kaca | 0,878 | 0,881 | 0,942 | 0,773 |
| Botol HDPE | 0,816 | 0,971 | 0,963 | 0,860 |
| Botol PET | 0,881 | 0,955 | 0,941 | 0,779 |

Kaleng masih mempunyai recall paling rendah. HDPE mempunyai sampel test
sedikit; nilai tinggi tidak membuktikan cakupan semua jenis wadah HDPE.
mAP bukan persentase kepastian suatu prediksi atau akurasi kamera pengguna.

## Perbandingan yang setara dengan baseline

COCO tidak memiliki empat kelas material ini. Karena itu, perbandingan
dibatasi pada **lokalisasi botol**, dengan tiga kelas botol kandidat digabung
dan dibandingkan dengan kelas COCO `bottle`, pada 429 gambar yang sama,
conf 0,25 dan IoU 0,5. Prediksi ganda yang tidak mendapat pasangan GT tetap FP.
Kaleng bukan kelas baseline; tidak ada klaim mAP empat kelas baseline nol.

| Ukuran | COCO YOLO11n | Kandidat fine-tuning |
|---|---:|---:|
| True positive botol | 47 | 360 |
| False positive botol | 4 | 125 |
| False negative botol | 324 | 11 |
| Precision botol | 0,922 | 0,742 |
| Recall botol | 0,127 | 0,970 |
| Latency GPU p50 | 11,43 ms | 12,17 ms |
| Latency GPU p95 | 14,22 ms | 16,91 ms |

Kandidat menemukan jauh lebih banyak botol pada dataset ini, dengan lebih
banyak false positive. **Peningkatannya tidak berlaku untuk semua metrik.**
Latency mencakup baca file dan predict setelah warmup; tidak mencakup load
model, jaringan, backend atau Gemini. Checksum baseline tetap
`0ebbc80d4a7680d14987a577cd21342b65ecfd94632bd9a8da63ae6417644ee1`.

## Bukti integrasi dan batas penggunaan

Checkpoint final diuji pada browser melalui unggah foto validation, bbox
nyata, pilihan kaleng dari daftar, konfirmasi kondisi eksplisit, **Gemini API
langsung → Recycle**, alasan dan sumber EPA. Tiga pemeriksaan integrasi
lulus, tanpa error JavaScript. Foto tidak dikirim ke Gemini. Screenshot:
`ai/artifacts/finetuned-demo.png`; laporan: `finetuned-demo-browser.json`.
Pada sampel tersebut, hipotesis kaleng 58% dan kaca 63% bertumpuk; pengguna
memilih kaleng lewat daftar. Ini bukti alur dan contoh ambiguitas, bukan
bukti model pasti benar. Kamera fisik belum diuji.

Regresi: **127 tes Python lulus**, 14 pemeriksaan browser baseline lulus
(YOLO nyata, rekomendasi fixture), dan wheel resources/exclusions terverifikasi.

Pembagian publik hanya mempunyai lima kelompok proxy test. Blok urutan
dan pHash mengurangi kebocoran, tetapi sumber tidak memberi identitas benda
fisik/sesi terverifikasi. Benda fisik yang sama mungkin masih muncul pada
split berbeda. Test bukan holdout pilot Indonesia atau uji negatif/OOD;
cutoff awal belum dikalibrasi pada pengujian bobot ini; pembaruan pemilihan cutoff val dicatat terpisah. Jangan menggunakan angka ini sebagai klaim
akurasi produksi atau alasan mengganti default semua objek.

Botol HDPE kini mempunyai panduan khusus bekas pangan/minuman dengan
konfirmasi isi sebelumnya dan penerima; tidak memakai panduan PET. Kardus,
baterai dan cangkir belum dilatih oleh kandidat ini. Material, kondisi dan
penerimaan fasilitas tetap dikonfirmasi pengguna.

Untuk mencoba dari root repository:

```powershell
.\ai\demo-camera-trained.bat
```

Launcher memakai environment demo awal dan CPU. Setup GPU, training/resume,
worker backend, dan verifikasi integrasi ada di [FINE_TUNING](FINE_TUNING.md).

Pembaruan 20 Oktober setelah evaluasi bobot: [profil keputusan dw-val-01](DETECTION_QUALITY.md) dipilih hanya pada validation. Botol kaca tetap UNCERTAIN; hasil OFFERED mengutamakan precision dengan penurunan coverage. [Panduan dan eval Gemini](RECOMMENDATION_EVALUATION.md) terpisah dari metrik deteksi.
