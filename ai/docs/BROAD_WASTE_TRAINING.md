# Training detektor dengan variasi objek lebih luas

20 Oktober 2026. Kandidat ini memakai taksonomi bentuk benda, bukan klasifikasi metode penanganan atau kepastian bahan. Model COCO default dan checkpoint khusus empat kelas lama tetap tersedia.

## Data yang benar-benar dipakai

Gabungan **3.699 gambar**: Drinking Waste v2 sebanyak **2.710**, COCO train2017 **880**, dan TACO **109**. Split akhir: **2.118 train, 925 validation, 656 test**. Data lokal, foto, dan bobot tidak dimasukkan ke Git.

| ID / kelas visual | Instance train | Validation | Test |
|---|---:|---:|---:|
| 0 / bottle | 1.761 | 684 | 501 |
| 1 / can | 453 | 169 | 132 |
| 2 / cup | 1.333 | 281 | 277 |
| 3 / plastic_bag | 6 | 1 | 1 |
| 4 / paper | 7 | 1 | 1 |
| 5 / cardboard | 17 | 3 | 1 |
| 6 / drink_carton | 3 | 0 | 0 |
| 7 / plastic_wrapper | 22 | 4 | 5 |
| 8 / food_container | 6 | 1 | 1 |
| 9 / bottle_cap | 23 | 3 | 3 |

Jumlah instance tidak sama dengan jumlah gambar. Delapan kelas tambahan mempunyai kekuatan data yang berbeda; khususnya `drink_carton` belum memiliki contoh evaluasi. **Sepuluh keluaran bukan sepuluh kelas yang sudah terbukti andal.**

Sumber dan keputusan pemakaian:

- **Drinking Waste v2:** data lama tetap beratribusi Serezhkin/TrashNet. Tiga label material botol digabung menjadi `bottle`, dan aluminium can menjadi `can`. Train diambil deterministik maksimal 400 gambar per kelas material asal agar botol tidak mendominasi; validation/test lama tetap dicadangkan. [Riset dan atribusi awal](DATASET_RESEARCH.md), [notices](DATASET_NOTICES.md).
- **COCO 2017:** anotasi dari [bucket resmi](https://s3.amazonaws.com/images.cocodataset.org/annotations/annotations_trainval2017.zip), subset foto dengan bbox cup dan seluruh bottle yang teranotasi pada foto tersebut. Pemilihan dibatasi license ID 4/5/7/8 pada metadata gambar penerbit. URL asli dan lisensi masing-masing dicatat; ini bukti deklarasi penerbit, bukan pemeriksaan ulang semua halaman Flickr. Anotasi COCO tidak memiliki semua kelas sampah tambahan. Dari 900 foto awal, **20 dikeluarkan** setelah screening visual karena ada/suspek target tambahan yang belum dianotasi; daftar audit memuat 23 ID, tiga di antaranya tidak terdapat dalam subset. [COCO](https://cocodataset.org/), [audit lokal](../evaluations/broad-waste-v1-data-audit.json).
- **TACO:** [anotasi utama penulis](https://github.com/pedropro/TACO), bukan unofficial. Sebanyak **109** foto lolos pemeriksaan lisensi langsung pada halaman foto Flickr saat ini. Pembatasan HTTP 429 menyebabkan banyak foto lain belum terverifikasi; itu tidak berarti foto tersebut pasti tidak berlisensi. Foto dengan hak belum jelas, atau URL OpenLitterMap dengan ketentuan foto belum dipastikan, tidak dimasukkan. MIT toolkit tidak dianggap lisensi semua foto.
- **Open Images tidak dipakai dalam run ini:** metadata train bbox dan metadata gambar masing-masing berukuran sekitar 2,26 GB dan 2,68 GB. COCO menyediakan anotasi cup yang dapat dipilih lebih langsung untuk eksperimen lokal ini. Jangan melaporkan run ini sebagai training Open Images.

Foto diubah ukurannya dengan mempertahankan rasio, EXIF dibuang, bbox dinormalisasi terhadap pixel tersimpan. Deduplikasi lintas sumber menggunakan hash dan pHash. Split mengikuti proxy kelompok data lama, blok nama file TACO, dan identitas foto COCO; nama pembuat foto COCO tidak tersedia dari metadata yang dipakai, sehingga pengelompokan menurut fotografer belum dilakukan. Ini **belum membuktikan objek fisik/sesi kamera terpisah**. Semua 17 contact sheet tambahan diperiksa sebagai screening, bukan sertifikasi setiap bbox pada resolusi penuh. Keberadaan objek kecil/tertutup yang belum teranotasi masih mungkin.

Metadata/hash/atribusi per foto ada di `ai/data/local/broad-waste-v1/manifest.json`; dataset dan run memiliki `DATASET_NOTICES.md`. Foto tidak dikirim ke Gemini atau disimpan sebagai lampiran pengguna.

## Menjalankan ulang training

Gunakan environment CUDA yang sudah tersedia. Jalankan dari root repo:

```powershell
.\.venv-ai\Scripts\python.exe ai/scripts/prepare_broad_waste.py --stage sources
.\.venv-ai\Scripts\python.exe ai/scripts/prepare_broad_waste.py --stage prepare --coco-limit 900
.\.venv-ai\Scripts\python.exe ai/scripts/audit_broad_waste.py
.\.venv-ai-train\Scripts\python.exe ai/scripts/train_broad_waste.py --epochs 35
```

Preparation menolak menimpa dataset yang sudah ada. Training juga menolak run bernama sama; gunakan `--name broad-waste-v1-rerun` untuk run lain. Sumber Flickr dapat berubah/tidak tersedia/dibatasi; cache dan hash manifest run aktual menentukan data yang benar-benar digunakan, bukan janji reproduksi byte yang sama dari internet kapan pun.

Konfigurasi: YOLO11n pretrained COCO, 640 px, batch 16, AdamW lr 0,001, cosine schedule, seed 42, maksimal 35 epoch, patience 10, mosaic 0,5, degrees 15, close mosaic 6, AMP. Bobot terbaik dipilih dari validation. Padding **square 640** dipakai untuk evaluasi per gambar dan demo kandidat agar konsisten; profil kalibrasi empat kelas lama tidak dipakai ulang.

Log: `ai/artifacts/training/broad-waste-v1-training.log`. Progres tiap epoch: `ai/artifacts/training/broad-waste-v1-yolo11n/progress.json`. Report akhir dan confusion matrix disalin ke `ai/evaluations/` oleh script setelah evaluasi selesai.

## Mencoba kamera dan LLM

```powershell
.\ai\demo-camera-broad.bat --port 8095
```

Buka alamat lokal yang dicetak terminal, aktifkan kamera atau unggah foto, deteksi, pilih bbox, lalu konfirmasi jenis/material. Launcher ini memilih **kandidat kedua YOLO11s**, sebagai eksperimen dengan review wajib. Pilih Gemini untuk rekomendasi langsung memakai konfigurasi `ai/.env` yang sudah ada; key tidak ditempatkan di frontend. Untuk mencoba tanpa panggilan API, pilih provider fixture dari demo.

Label `bottle` menawarkan PET/HDPE/kaca; `can` menawarkan aluminium/baja; `cup` menawarkan beberapa material. Jangan menganggap label umum tersebut membuktikan bahan. Mapping versi `broad-waste-objects-01`; profil `broad-experimental-01` membuat **semua kelas UNCERTAIN/perlu ditinjau**, termasuk confidence tinggi. Ambang 0,50 dipakai untuk seleksi yang ditampilkan; belum ada cutoff OFFERED terkalibrasi untuk kandidat ini.

Katalog kini 78 pilihan dengan panduan DRAFT. Tambahan kantong/film, kertas, kaleng baja, wadah plastik, dan tutup memakai saran Recycle bersyarat: pengelola harus menerima jenis benda/bahan yang tepat. Wadah bekas pangan perlu konfirmasi isi sebelumnya. **Paper cup dan drink carton belum memiliki evidence penanganan khusus**: dalam mode HANDLING dapat menghasilkan NEEDS_CONTEXT/UNAVAILABLE, dan tidak dipaksakan menjadi Recycle. REDUCE tetap hanya untuk PREVENTION. Kekurangan evidence tidak ditutup dengan jawaban bebas LLM.

## Integrasi backend dan frontend

Kontrak HTTP tetap [HTTP AI v1](AI_HTTP_CONTRACT.md). Jalankan worker YOLO dengan checkpoint kandidat dan padding yang sama:

```powershell
.\.venv-ai\Scripts\python.exe -m wastenot_ai.cli serve-worker --port 8091 --model ai/artifacts/training/broad-waste-v2-yolo11s/weights/best.pt --input-shape square
```

Gunakan `AI_SERVICE_TOKEN` untuk worker dan `AI_RECOMMENDATION_TOKEN` pada service rekomendasi seperti panduan integrasi utama. Backend membaca capabilities, lalu menyimpan `class_names`, `model_version`, `model_sha256` dan `mapping_version` dari snapshot worker. Jangan memakai ID COCO untuk checkpoint ini. Kirim snapshot pilihan tersebut ke service rekomendasi; frontend menampilkan band dan pilihan material dari Python.

Tidak ada perubahan endpoint Go/Android produksi dalam eksperimen ini. Tidak ada penggantian model default atau push GitHub otomatis.

## Membaca hasil

Putaran pertama YOLO11n selesai **30 epoch**, early stopping, best epoch 20. Bobot SHA-256 `cea6211513e461fcd7acb6717332e3c8426521466c46d471baf6878e7d96510f`. [Report putaran pertama](../evaluations/broad-waste-v1-20261020.json).

Pada validation yang sama, confidence 0,50 dan square 640:

| Model | Bottle P / R | Can P / R | Cup P / R |
|---|---|---|---|
| Broad v1 YOLO11n | 90,27% / 63,74% | 82,01% / 67,46% | 56,17% / 32,38% |
| COCO YOLO11n | 97,33% / 21,35% | Tidak ada kelas | 84,46% / 44,48% |
| Khusus empat kelas lama, botol digabung | 54,29% / 71,20% | 87,72% / 59,17% | Tidak ada kelas |

Test eksploratif putaran pertama: mAP50 **0,2753**, mAP50:95 **0,1835**; macro mencakup **sembilan kelas dengan GT**, bukan drink_carton. AP50 bottle 0,7699, can sekitar 0,833, cup sekitar 0,445. Enam kelas langka mempunyai nol true positive pada cutoff operasional validation 0,50; drink_carton tidak memiliki GT validation. **Kandidat ini tidak dipromosikan sebagai peningkatan umum.**

Karena cup turun dibanding COCO, dilakukan eksperimen kedua **YOLO11s**, dengan data/split yang sama. Classifier hidden width YOLO11s cocok saat nc 80 menjadi 10; enam tensor output terakhir berbeda. Callback menyalin baris classifier COCO bottle 39 menjadi kelas 0 dan cup 41 menjadi kelas 2, memeriksa kecocokan dimensi, serta menyinkronkan EMA. Kelas lain tetap berinisialisasi baru. Konfigurasi kedua: backbone freeze 10, lr 0,0003, maksimum 30 epoch; sumber bobot resmi `yolo11s.pt`, SHA `85a76fe86dd8afe384648546b56a7a78580c7cb7b404fc595f97969322d502d5`.

```powershell
.\.venv-ai-train\Scripts\python.exe ai/scripts/train_broad_waste.py --name broad-waste-v2-yolo11s --initial-model ai/artifacts/models/yolo11s.pt --seed-known-head --freeze 10 --lr0 0.0003 --epochs 30
```

Transfer kelas ini adalah eksperimen implementasi lokal, bukan janji peningkatan. Detail nyata disimpan pada `head-transfer.json` dan `args.yaml` di run kedua. Bobot dasar diunduh dari [rilis Ultralytics](https://github.com/ultralytics/assets/releases/download/v8.3.0/yolo11s.pt).

Putaran kedua selesai **18 epoch**, best epoch **8**, early stopping. SHA-256 bobot `a3f34225287ad8dd01fe9082488db3f9c40dea5b7a90b8a19f1e6bc77d69a069`. Test eksploratif: mAP50 **0,3644**, mAP50:95 **0,2362**, sembilan kelas dengan GT. AP50 bottle **0,8389**, can **0,9251**, cup **0,6839**. Macro tersebut juga dipengaruhi kelas yang hanya memiliki satu contoh, sehingga tidak menjadi ukuran kesiapan lapangan.

| Model pada validation, cutoff 0,50 | Bottle P / R | Can P / R | Cup P / R |
|---|---|---|---|
| Broad v2 YOLO11s | 79,69% / 82,02% | 97,92% / 55,62% | 76,85% / 55,52% |
| Broad v1 YOLO11n | 90,27% / 63,74% | 82,01% / 67,46% | 56,17% / 32,38% |
| COCO YOLO11n | 97,33% / 21,35% | Tidak ada kelas | 84,46% / 44,48% |

V2 lebih baik dari v1 untuk precision dan recall cup, tetapi dibanding COCO recall naik dengan penurunan precision. Pada cutoff yang sama, belum ada true positive validation untuk kelas langka tambahan; drink_carton tetap tanpa GT validation. Gate kegunaan awal untuk bottle (P≥0,85/R≥0,60), can (P≥0,80/R≥0,65), cup (P≥0,80/R≥0,40) belum semuanya terpenuhi. **Tidak ada promosi menjadi model default, dan tidak ada klaim bahwa sepuluh kelas sudah andal.** Launcher broad memilih v2 untuk mencoba cakupan eksperimen dengan semua hasil UNCERTAIN.

[Perbandingan dua run](../evaluations/broad-waste-comparison-20261020.json), [report lengkap v2](../evaluations/broad-waste-v2-yolo11s-20261020.json), [confusion matrix v2 pada cutoff 0,50](../evaluations/broad-waste-v2-yolo11s-val-conf050-confusion_matrix.png).

Verifikasi: **186 tes Python**, **44/44 skenario rekomendasi offline**, 34 artifact kontrak cocok dengan generator, wheel beserta resource lolos pemeriksaan, dan seluruh 3.699 hash foto/split/label sesuai manifest. Terdapat 15 bbox lama dengan pembulatan tepi sampai 0,001954, tetap dalam toleransi audit sumber 0,002; anotasi lama tidak diam-diam diubah saat training.

Masing-masing kandidat lulus 16 pemeriksaan browser dengan kamera Chromium palsu, tanpa error JavaScript. Alur nyata YOLO → adapter HTTP privat → keputusan fixture dan Gemini juga lulus pada foto cup dengan kondisi kosong: READY/REUSE, basis CONDITIONAL, tanpa menyimpan log atau mengirim foto ke Gemini. V1 gagal menampilkan cup pada salah satu contoh validation (`42735`, confidence sekitar 0,156), sedangkan v2 lulus pada contoh yang sama; ini smoke integrasi, bukan bukti akurasi lapangan. Laporan browser/HTTP tersimpan terpisah di `ai/artifacts/browser-broad-v1`, `browser-broad-v2`, dan `http-broad*.json`.

Klarifikasi metadata: field `creator_page` COCO dalam snapshot input lama berisi URL foto asli penerbit, bukan identitas pembuat foto. Group COCO pada run aktual adalah identitas URL foto ditambah grouping pHash lintas sumber; **split menurut fotografer belum dilakukan**. Input manifest dibekukan dan hash-nya dipertahankan; report memberi errata tersebut, dan script persiapan sudah diperjelas untuk run berikutnya.

AP dievaluasi dengan confidence awal 0,001; precision/recall operasional dihitung terpisah pada confidence 0,50, matching IoU 0,50, NMS IoU 0,70, max_det 50, square 640. Confusion matrix yang dibagikan memakai cutoff 0,50 tersebut, sehingga tidak mencampur ribuan prediksi sangat rendah dengan perilaku demo.

Perbandingan baseline COCO hanya untuk bottle/cup; model lama hanya untuk bottle/can dengan tiga label botol digabung. Tidak membuat skor nol palsu untuk kelas yang memang tidak ada pada baseline. Penggabungan label bahan mengubah tugas; jangan membandingkan mAP empat kelas lama langsung dengan mAP sepuluh kelas baru.

COCO train2017 sudah dilihat bobot pretrained, dan test Drinking Waste lama pernah diperiksa. Hasil gabungan ini bukan holdout independen sepenuhnya. Bukti kualitas lapangan tetap memerlukan kamera lokal dengan benda/sesi baru dan negatif nyata; tidak ada klaim peningkatan universal sebelum bukti tersebut.
