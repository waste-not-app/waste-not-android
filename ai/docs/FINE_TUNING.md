# Fine-tuning YOLO untuk WasteNot

Perluasan objek setelah eksperimen empat kelas ini didokumentasikan terpisah pada [Broad Waste: training, hasil, demo dan integrasi](BROAD_WASTE_TRAINING.md).

Eksperimen lokal ini memakai **Drinking Waste Classification v2** dari Arkadiy
Serezhkin. Lihat [riset sumber](DATASET_RESEARCH.md) dan
[atribusi/lisensi](DATASET_NOTICES.md). Foto, arsip, anotasi, log training, dan
bobot tetap di direktori lokal yang diabaikan Git. Foto kamera pengguna dan
sampel TACO smoke **tidak** dimasukkan ke training.

Run pertama sudah selesai: 30 epoch melalui early stopping, checkpoint
terbaik epoch 18. Hasil test, perbandingan baseline, hash dan bukti Gemini
langsung tersedia pada [laporan hasil](FINETUNING_RESULTS.md).

## Dataset yang benar-benar dipakai

Arsip v2: SHA-256
`2231be78c398a8606b29399ccc8797be0586d5c4894d610d4ff5e84494dd1449`.
Script memverifikasi checksum sebelum menyiapkan data.

| ID checkpoint | Nama kelas | Mapping demo |
|---|---|---|
| 0 | aluminium_can | Kaleng aluminium; konfirmasi pengguna |
| 1 | glass_bottle | Botol kaca; konfirmasi pengguna |
| 2 | hdpe_bottle | Panduan HDPE sendiri; konfirmasi bekas pangan/minuman dan penerima, tanpa REUSE otomatis |
| 3 | plastic_bottle | Kandidat botol PET; material tetap dikonfirmasi |

Arsip menyediakan 4.821 JPEG pada `YOLO_imgs`, 4.811 file label, dan delapan
HEIC yang tidak diproses. Meskipun deskripsi menyebut `labels.txt`, file itu
tidak ada. ID diperiksa terhadap seluruh kelompok nama sumber
`AluCan/Glass/HDPEM/PET`; kombinasi `mix` mempertahankan ID yang sama.
`rawimgs` tidak dimasukkan kembali sebagai sampel tambahan.

Setelah audit: **4.572 gambar**, 249 ditolak: 62 duplikat identik, 177 anotasi
geometri tidak valid, dan 10 gambar tanpa file label. Tidak ada bbox yang
dibuat otomatis dari label klasifikasi.

Ke-62 duplikat memiliki ID kelas yang sama tetapi koordinat bbox yang sedikit
berbeda (IoU minimum 0,751; median 0,947 pada audit pasangan). Eksperimen mempertahankan anotasi valid pertama menurut urutan nama
file, tidak merata-ratakan bbox. Audit perbandingan lokal disimpan pada
`ai/artifacts/dataset-inspect/duplicate-label-audit.json`. Script menolak
duplikat dengan kelas yang bertentangan untuk ditinjau, bukan memilih label
secara otomatis.

| Split | Gambar | Kaleng | Kaca | HDPE | PET |
|---|---:|---:|---:|---:|---:|
| Train | 3.462 | 795 | 1.030 | 791 | 1.012 |
| Validation | 681 | 169 | 148 | 185 | 179 |
| Test eksploratif | 429 | 132 | 151 | 34 | 186 |

Kolom kelas menghitung instance, sehingga jumlahnya dapat melebihi gambar.
Manifest mencatat file sumber, hash, bbox, kelas, split, dan kelompok setiap
gambar. Gambar diperkecil dengan rasio tetap, sisi maksimal 960, kemudian
training memakai input 640. Anotasi mengikuti piksel tersimpan, sehingga
orientasi EXIF tidak diubah secara terpisah dari bbox.

Pembagian memakai blok urutan 200 foto dan komponen near-duplicate pHash
jarak maksimal 5; 22 pasangan dekat digabung sebelum split. Total kelompok:
18 train, empat validation, lima test. Seed 42. Sumber tidak menyediakan ID
benda fisik/sesi yang terverifikasi: ini **proxy**, bukan bukti benda fisik
antar-split berbeda. Tidak ada himpunan negatif/OOD terverifikasi. Angka
test merupakan eksperimen publik, belum memenuhi release gate pilot.

## Lingkungan training terpisah

Dari root repo, PowerShell:

```powershell
python -m venv .venv-ai-train
.\.venv-ai-train\Scripts\python.exe -m pip install torch==2.9.1 torchvision==0.24.1 --index-url https://download.pytorch.org/whl/cu128
.\.venv-ai-train\Scripts\python.exe -m pip install -e "./ai[yolo,worker,test]" -c ai/constraints-training.txt
.\.venv-ai-train\Scripts\python.exe -c "import torch; print(torch.__version__, torch.cuda.is_available()); print(torch.cuda.get_device_name(0))"
```

Jangan memakai constraint CPU `ai/constraints.txt` untuk instalasi CUDA ini.
Lingkungan lokal saat pengerjaan memakai `--system-site-packages`; torch dan
torchvision CUDA dipasang di venv tersebut. `.venv-ai` demo awal tetap terpisah.
Resep instalasi CUDA mengacu pada [pasangan versi resmi PyTorch](https://pytorch.org/get-started/previous-versions/).

## Unduh, audit, train, dan evaluasi

```powershell
.\.venv-ai-train\Scripts\python.exe -m wastenot_ai.cli fetch-assets
.\.venv-ai-train\Scripts\python.exe ai/scripts/prepare_drinking_waste.py --download
.\.venv-ai-train\Scripts\python.exe ai/scripts/train_waste_yolo.py --epochs 40 --batch 16 --device 0
```

Script prepare menolak output yang sudah ada; gunakan `--output` baru untuk
eksperimen berbeda. Arsip yang telah diunduh digunakan kembali. Script train
menolak nama run yang sudah ada; gunakan `--name` baru atau resume eksplisit.
Tidak perlu Gemini key untuk training. Telemetri dan integrasi tracker
eksternal dimatikan; training dan evaluasi berjalan lokal.

Konfigurasi awal: pretrained YOLO11n, 640, batch 16, AdamW lr0=0,001,
40 epoch maksimal, early stopping patience 12, seed 42, workers 4, AMP aktif
setelah pemeriksaan kompatibilitas. Gunakan `--workers 0 --no-amp` jika runtime
lain membutuhkan fallback. Script memiliki main guard untuk multiprocessing
Windows. Checkpoint dipilih dari validation, lalu dievaluasi
pada test tetap. Detail final tersedia di `args.yaml` dan `results.csv` run.

```powershell
# Lanjutkan training yang benar-benar terputus, termasuk optimizer/epoch:
.\.venv-ai-train\Scripts\python.exe ai/scripts/train_waste_yolo.py --resume ai/artifacts/training/drinking-waste-v2-yolo11n/weights/last.pt
```

Hasil lokal:

- `ai/data/local/drinking-waste-v2/manifest.json`: inventaris, penolakan dan split.
- `ai/artifacts/training/drinking-waste-v2-yolo11n/weights/best.pt`: kandidat pilihan validation.
- `ai/artifacts/training/drinking-waste-v2-yolo11n/results.csv`: kurva metrik training/validation.
- `ai/artifacts/training/drinking-waste-v2-yolo11n/test/`: evaluasi/confusion matrix test.
- `ai/artifacts/training/drinking-waste-v2-yolo11n/evaluation.json`: per kelas, hash, runtime dan perbandingan baseline.

Perbandingan dengan COCO dibatasi pada **lokalisasi botol**: tiga kelas botol
kandidat digabung dan dibandingkan dengan kelas COCO `bottle`, conf 0,25,
IoU 0,5, gambar test sama. Ini tidak menilai kebenaran material. COCO tidak
mempunyai kelas kaleng aluminium; mAP empat kelas kandidat bukan mAP COCO.
Latency laporan meliputi pembacaan file dan predict setelah warmup, bukan
latency jaringan/backend/LLM. Jangan tuning ulang berdasarkan test ini lalu
menyebutnya holdout yang belum pernah dilihat.

## Mencoba checkpoint pada demo YOLO–Gemini

Jika environment demo awal `.venv-ai` sudah terpasang, launcher ini memakai
checkpoint hasil training dengan inference CPU:

```powershell
.\ai\demo-camera-trained.bat
```

Untuk inference GPU, gunakan interpreter CUDA secara langsung:

```powershell
.\.venv-ai-train\Scripts\python.exe -m wastenot_ai.cli demo --demo-guides --device 0 --model ai/artifacts/training/drinking-waste-v2-yolo11n/weights/best.pt
```

Kamera, pemilihan objek, konfirmasi kondisi, dan Gemini tetap memakai alur
yang sama. `ai/.env` hanya diperlukan untuk rekomendasi Gemini. Mapping
`drinking-waste-reviewed-02` aktif hanya jika **seluruh** taksonomi checkpoint
sesuai; ID 0 tidak diperlakukan sebagai `person` COCO. HDPE mempunyai panduan tersendiri dengan konfirmasi isi/penerima.

Model khusus ini hanya memiliki empat kelas. Untuk cangkir, buku, dan
variasi objek demo yang luas, jalankan demo biasa dengan YOLO11n COCO:

```powershell
.\.venv-ai\Scripts\python.exe -m wastenot_ai.cli demo --demo-guides
```

Uji integrasi browser untuk model khusus, dengan server pada port 8095 dan
foto yang memang berisi kelas target:

```powershell
.\.venv-ai\Scripts\python.exe ai/scripts/verify_finetuned_demo.py --url http://127.0.0.1:8095 --image path/to/can.jpg --object-key aluminium_can
# Tambahkan --live-gemini untuk satu request Gemini nyata; default memakai fixture.
```

Script ini memerlukan Playwright seperti verifier demo biasa. Ia memeriksa
scope katalog aktif, bbox nyata, pilihan dari daftar, konfirmasi konteks,
hasil/sumber dan pembersihan foto. Jika bbox kandidat bertumpuk, pilih objek
dari daftar. Ini uji integrasi dengan kondisi yang diisi eksplisit, bukan
pengukuran akurasi atau bukti keadaan benda dari foto.

Backend privat dapat memilih checkpoint yang sama dengan `serve-worker
--model ... --device 0` atau `AI_MODEL_PATH`/`AI_DEVICE`. Pertahankan hash,
daftar kelas, mapping_version, dan hasil evaluasi bersama artifact saat
handoff. Jangan mengganti model default produksi berdasarkan satu metrik
publik. Baterai, kardus, cangkir, kontaminasi, keamanan bahan dan kualitas
rekomendasi LLM tidak dilatih oleh eksperimen empat kelas ini.

Sesudah training, profil threshold/ambiguitas `dw-val-01` dipilih hanya pada val. Hasil dan cara mengumpulkan/mengukur negative/OOD ada di [DETECTION_QUALITY](DETECTION_QUALITY.md). Ini tidak mengubah bobot atau membuktikan holdout kamera telah lulus.
