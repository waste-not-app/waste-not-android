# Mutu keputusan YOLO

20 Oktober 2026. Checkpoint tetap `af1446f33338…`; pembaruan ini mengubah cara meninjau prediksi, bukan melatih bobot baru. Model COCO tetap default. Kandidat empat kelas digunakan melalui `ai/demo-camera-trained.bat` atau `--model`.

## Pengukuran dan keputusan

681 gambar validation, 681 anotasi, matching kelas yang sama dengan IoU ≥0,50; satu ground truth hanya boleh dipasangkan sekali, prediksi berlebih menjadi FP. Inference: 640, conf 0,25, class-aware NMS IoU 0,70, max_det 50, GPU. Threshold dipilih **hanya pada val**, tanpa membaca test untuk penyetelan. Data/split/model dikunci dengan SHA-256 pada [laporan JSON](../evaluations/yolo-validation-20261020.json).

| Kelas | Batas tinjau | Batas OFFERED | Keputusan |
|---|---:|---:|---|
| aluminium_can | 0,50 | 0,70 | Ditawarkan dengan konfirmasi |
| glass_bottle | 0,50 | Dinonaktifkan | Selalu UNCERTAIN, termasuk skor 1,0 |
| hdpe_bottle | 0,50 | 0,70 | Konfirmasi material dan isi sebelumnya |
| plastic_bottle | 0,50 | 0,725 | Konfirmasi PET |

Aturan pencarian: batas tinjau minimal 0,50, target precision empiris 0,85, minimal 20 TP; batas OFFERED minimal 0,70, target precision 0,95, minimal 20 TP. Jika target tinjau gagal, tetap 0,50 dengan keterbatasan tercatat. Jika target OFFERED gagal, kelas tetap terlihat untuk ditinjau tetapi tidak ditawarkan sebagai OFFERED. Target ini bukan interval kepercayaan atau jaminan akurasi.

Botol kaca hanya mencapai precision 0,679 pada batas 0,70. Tidak ada cutoff pada grid yang diuji yang memenuhi target OFFERED dengan dukungan minimum. Menaikkan confidence global tidak menyelesaikan masalah kelas ini.

| Keputusan pada empat kelas | TP | FP | FN | Precision | Recall |
|---|---:|---:|---:|---:|---:|
| Semua prediksi ≥0,50, sebelum/sesudah | 558 | 122 | 123 | 82,06% | 81,94% |
| OFFERED dengan batas global lama 0,70 | 540 | 73 | 141 | 88,09% | 79,30% |
| OFFERED dengan profil baru | 411 | 12 | 270 | 97,16% | 60,35% |

Perbandingan memakai empat kelas yang sama, termasuk HDPE, untuk mengisolasi perubahan threshold; bukan perbandingan jumlah panduan versi lama. Naiknya precision OFFERED dibayar penurunan recall OFFERED. Kandidat ≥batas tinjau tetap bisa dipilih sebagai UNCERTAIN. Ini **hasil pada data yang dipakai memilih cutoff**, bukan hasil holdout independen, peningkatan mAP bobot, atau probabilitas benar 97% pada kamera pengguna. Test publik yang sebelumnya sudah dievaluasi juga bukan holdout baru.

## Hipotesis kelas yang bertumpuk

Prediksi berbeda kelas dengan IoU ≥0,65 dan keduanya lolos batas tinjau tetap disimpan, saling menunjuk melalui `ambiguous_with`, diberi `OVERLAPPING_CLASSES`, dan menjadi UNCERTAIN. Tiga gambar val memiliki konflik tersebut. Tidak ada hipotesis yang dihapus hanya karena skor kelas lain lebih tinggi. Benda yang terpisah dan dua prediksi kelas yang sama tidak dianggap konflik ini.

Frontend menampilkan “Kelas bertumpuk · periksa” dan menyarankan memilih lewat daftar. Ini heuristik peninjauan, bukan bukti bahwa kedua bbox pasti satu benda fisik. Pengguna tetap mengonfirmasi material, termasuk OFFERED.

Profil `dw-val-01` hanya aktif untuk **checksum penuh, model_version dan taksonomi lengkap** yang cocok dengan [detection_profile.json](../src/wastenot_ai/data/detection_profile.json). Mapping aktif kandidat menjadi `drinking-waste-reviewed-02+dw-val-01`. Checkpoint lain tetap memakai batas katalog. Jangan menyalin cutoff ke model lain tanpa pengukuran.

## Integrasi

Respons deteksi menambahkan `review_profile`; setiap objek mempunyai `review_thresholds`, `review_reasons`, dan `ambiguous_with`. Gunakan confidence_band dari server, jangan hitung ulang pada Go/Android. Capability menyertakan class_thresholds/review_only_classes. Top-level thresholds tetap default katalog; threshold objek lebih spesifik.

Go menyimpan snapshot penuh dan mengirim `model_sha256` dari respons privat sebagai bagian selection ke modul keputusan. Python mengaktifkan profil yang sama dan memeriksa mapping lagi. Selection tanpa checksum pada mapping berprofil ditolak sebagai INVALID_AI_REFERENCE; field ini tidak diterima dari frontend. Modul keputusan tidak membutuhkan GPU/bobot untuk replay aturan. Penghitungan ambiguity tidak diulang dari satu objek, sebab semua prediksi tetangganya tidak dikirim; keputusan tetap mensyaratkan konfirmasi dan cutoff tinjau yang sama.

## Reproduksi

```powershell
# Venv training/CUDA yang sudah disiapkan, ~681 inference; selanjutnya menggunakan cache terikat hash.
.\.venv-ai-train\Scripts\python.exe ai/scripts/calibrate_yolo.py --device 0
# Enam pola sintetis, tidak mengakses kamera atau Gemini.
.\.venv-ai-train\Scripts\python.exe ai/scripts/evaluate_detection_quality.py --synthetic --device 0
# Tes perilaku threshold, konflik dan provenance tanpa model/GPU.
.\.venv-ai\Scripts\python.exe -m pytest ai/tests/test_detection_quality.py -q
```

Cache raw prediksi berada di `ai/artifacts/calibration/validation-predictions.json`, diabaikan Git. Script tidak otomatis mengganti profil distribusi; perubahan profil memerlukan pemeriksaan report, pembaruan JSON, regenerasi kontrak dan tes.

## Negatif, OOD dan holdout kamera

Enam gambar sintetis (empat warna polos, dua checkerboard) menghasilkan nol kandidat tinjau/OFFERED pada checkpoint ini; [laporan](../evaluations/detection-synthetic-20261020.json). **Ini sanity check, bukan pengukuran penolakan objek nyata di luar cakupan.** Belum ada holdout kamera Indonesia berlabel atau negative/OOD terverifikasi; tidak ada angka akurasi dunia nyata yang dibuat dari pola tersebut.

Gunakan template [camera-holdout-manifest.json](../examples/camera-holdout-manifest.json), salin ke `ai/data/local/camera-holdout/manifest.json`, lalu isi foto sendiri yang memang boleh digunakan. Ambil sesi dan benda fisik baru, pisahkan dari training/val. Target awal pengumpulan: ≥20 benda fisik per kelas dalam beberapa latar/pencahayaan, ≥50 foto negatif (ruang kosong, orang, meja tanpa target), dan ≥50 OOD (cangkir, keramik, mainan, baterai/kardus tanpa empat target). Angka tersebut adalah rencana pengumpulan, bukan dataset yang sudah ada atau ukuran release gate statistik.

Setiap record:

```json
{
  "id": "session-b-empty-desk-01",
  "image": "images/empty-desk-01.jpg",
  "sha256": "ISI_SHA256_FOTO_64_HEX",
  "capture_session_id": "session-b",
  "physical_object_ids": [],
  "reviewer": "ISI_PENINJAU_SEBENARNYA",
  "reviewed_at": "2026-10-20",
  "kind": "NEGATIVE",
  "truth": []
}
```

`kind=POSITIVE` memerlukan physical_object_ids dan seluruh bbox target `{class_id:0..3,bbox:[xmin,ymin,xmax,ymax]}` 0..1 sesuai foto setelah EXIF transpose. NEGATIVE/OOD memerlukan anotasi target kosong yang sudah diperiksa, termasuk latar. Jalankan tanpa menyetel threshold terhadap hasilnya:

```powershell
.\.venv-ai-train\Scripts\python.exe ai/scripts/evaluate_detection_quality.py --manifest ai/data/local/camera-holdout/manifest.json --device 0 --output ai/artifacts/camera-holdout-report.json
```

Evaluator memeriksa review/session, hash, duplikat dan kelas, lalu melaporkan TP/FP/FN serta jumlah gambar kosong yang mendapat kandidat salah, dipisahkan per kind dan band. Template kosong ditolak. Audit independensi benda/sesi tetap perlu dilakukan peninjau; program tidak dapat membuktikannya hanya dari nama ID. Simpan gambar di folder lokal yang diabaikan Git.
