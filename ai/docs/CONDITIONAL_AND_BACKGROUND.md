# Saran saat kondisi kosong dan audit kesalahan YOLO

20 Oktober 2026, `pilot-policy-06`. Pembaruan ini mengikuti permintaan agar kebersihan, keutuhan, kelayakan, niat pakai ulang, fokus dan akses daur ulang boleh dibiarkan belum tahu.

## Saran bersyarat dari Gemini

Pengguna tetap memilih/mengonfirmasi jenis benda. Field lainnya opsional: yang tidak diisi tetap UNKNOWN, niat pakai ulang tetap null, fokus default AUTO. Server mengirim fakta tersebut beserta panduan dan syarat yang belum diketahui kepada Gemini. Tidak ada isian yang otomatis dianggap bersih/utuh/layak atau penerima daur ulang yang direkayasa. Foto tetap diproses YOLO lokal; Gemini tidak menerima foto.

Contoh minimal:

```json
{"object_key":"ceramic_cup","category":"OTHER"}
```

Hasil dapat berupa **Reuse — saran sementara**, alasan, sumber dan langkah pemeriksaan. Reuse baru diikuti setelah keutuhan, kelayakan, kebersihan dan keinginan memakai ulang dipastikan. Botol PET yang kondisinya belum diketahui dapat mendapat **Recycle bersyarat**, tanpa mengklaim fasilitas sudah menerima. Jika ada kondisi yang sudah diisi, saran tidak boleh bertentangan dengannya.

| Field respons | Arti |
|---|---|
| status=READY, recommendation_basis=CONFIRMED | Syarat evidence dipenuhi oleh konteks yang diisi; tetap bukan sertifikasi keamanan dari foto |
| status=READY, recommendation_basis=CONDITIONAL | Satu saran tersedia; conditions_to_verify menyebut syarat yang masih perlu diperiksa |
| status=NEEDS_CONTEXT | Ada pertanyaan penting yang tidak boleh dilewati, misalnya jenis risiko atau riwayat isi HDPE |
| status=UNAVAILABLE | Tidak ada aturan yang cocok, kontaminasi di luar cakupan, atau provider/output tidak tersedia/valid |

Risk_flag dicentang tetapi jenisnya belum jelas tetap meminta penjelasan. Kontaminasi kimia/biologis tidak mendapat aturan cuci/residu biasa. Isi sebelumnya yang belum diketahui pada HDPE tetap ditanyakan. Reduce tetap untuk fokus PREVENTION; bukan pengganti kegagalan penanganan. Panduan belum dipublikasikan/kedaluwarsa tetap ditolak di luar mode demo.

Pemilihan bersyarat hanya mempertimbangkan evidence yang tidak bertentangan dengan nilai yang sudah diketahui. Gemini tidak boleh mengubah UNKNOWN menjadi fakta. Server menyusun alasan bersyarat dan mendahulukan langkah verifikasi sebelum langkah tindakan; teks bebas model tidak menjadi instruksi publik. Ketika syarat lengkap, respons kembali CONFIRMED.

## Backend dan frontend

Request publik menambahkan `allow_conditional` default **true**. Untuk alur yang harus menunggu semua syarat terisi, kirim false; NEEDS_CONTEXT lama tetap tersedia. Endpoint keputusan privat menerima flag yang sama pada context. Field yang kosong dikirim dengan menghilangkan field atau memakai UNKNOWN; reuse_intention boleh null. Fokus yang tidak diisi memakai AUTO. String kosong/null untuk enum lain bukan cara mengirim UNKNOWN.

Respons menambahkan recommendation_basis (CONFIRMED/CONDITIONAL/null) dan conditions_to_verify (field path yang sama seperti missing_context). READY berarti saran siap ditinjau, bukan bukti tindakan telah dilaksanakan. Frontend menampilkan label “Saran sementara”, syarat dan sumber; tidak mengisi nilai UNKNOWN atau menyimpan aktivitas otomatis. Perubahan kondisi membatalkan saran lama. Demo browser sudah memakai alur ini.

OpenAPI dan [contoh conditional-cup](../contracts/v1/examples/conditional-cup-response.json) telah diregenerasi. Python tetap memiliki aturan; Go tidak mengimplementasikan ulang seleksi evidence. Semua panduan tetap DRAFT untuk demo sampai review lokal yang sebenarnya.

```powershell
# Kamera dengan model terlatih:
.\ai\demo-camera-trained.bat --port 8095
# Coba tanpa mengisi kondisi, offline:
.\.venv-ai\Scripts\python.exe -m wastenot_ai.cli recommend --context ai/examples/context-unknown-cup.json --provider fixture --demo-guides
# Gemini nyata menggunakan key/model lokal:
.\.venv-ai\Scripts\python.exe -m wastenot_ai.cli recommend --context ai/examples/context-unknown-cup.json --provider gemini --demo-guides
# Evaluasi skenario terbaru:
.\.venv-ai\Scripts\python.exe ai/scripts/evaluate_recommendations.py --provider fixture
.\.venv-ai\Scripts\python.exe ai/scripts/evaluate_recommendations.py --provider gemini --max-calls 16
```

44/44 skenario offline lulus. Empat kasus baru membiarkan seluruh field opsional kosong (cangkir, PET, kaleng, baterai) dan lulus pada Gemini nyata. Run live penuh menghasilkan 42/44: dua kasus gagal karena LLM_UNAVAILABLE, kemudian dua kasus tersebut lulus pada percobaan eksplisit terpisah. Ini tidak dilaporkan sebagai 44/44 dalam satu run. Laporan [run penuh](../evaluations/recommendation-policy06-gemini-20261020.json), [percobaan ulang dua kasus](../evaluations/recommendation-policy06-provider-recheck-20261020.json), dan [fixture](../evaluations/recommendation-policy06-fixture-20261020.json). Enam kasus adversarial tetap output fixture yang disuntikkan; tidak dihitung sebagai panggilan Gemini.

## Memahami background pada confusion matrix

Model empat kelas tidak dilatih untuk mengeluarkan kelas kelima bernama background. Pada matriks Ultralytics, **baris adalah prediksi, kolom adalah label sebenarnya**. Kolom background menghitung prediksi tanpa pasangan anotasi; baris background menghitung objek beranotasi yang terlewat. Referensi [metrik Ultralytics](https://docs.ultralytics.com/guides/yolo-performance-metrics/) dan implementasi lokal pinned `ultralytics/utils/metrics.py:ConfusionMatrix.process_batch`.

Matriks test lama dibuat dengan default validator deteksi confidence **0,001**, sementara demo meninjau kandidat mulai 0,50. Ini menjelaskan mengapa jumlah kotak salah pada gambar matriks jauh lebih besar. Mengubah threshold tampilan matriks tidak menghilangkan kesalahan model; pengukuran harus menyebut confidence, matching IoU dan split.

Pada **681 gambar val yang sama**, batas 0,50 dan matching IoU 0,45 versi Ultralytics menghasilkan 25 prediksi tanpa pasangan (14 kaca, 6 HDPE, 5 PET). Banyak kesalahan lain justru pertukaran kelas; misalnya 55 anotasi kaleng dipasangkan dengan prediksi kaca. Lihat [matriks val pada 0,50](../evaluations/yolo-original-val-conf050.png). Ini berbeda dari scorer class-aware pada IoU 0,50 yang digunakan untuk menghitung precision/recall, sehingga jumlah TP/FP tidak dicampur antarprotokol.

## Eksperimen training

600 crop latar nyata dibuat dari **split train saja**, di luar seluruh bbox plus margin 0,04. Dari 2.400 kandidat, model lama memberi beberapa tekstur lantai/karpet/dinding skor sampai sekitar 0,84. Crop dipilih deterministik, dibatasi dua per foto sumber dan dideduplikasi. Training mencampur 3.462 foto positif dengan 600 crop negatif; val/test asli tidak diubah. Contact sheet 120 crop tersulit diperiksa secara visual. Sisa crop memakai pengecualian anotasi; ini bukan review ahli seluruh gambar atau dataset OOD independen.

Kandidat pertama fine-tune 12 epoch (early stop), best epoch 4, menghasilkan hasil berikut pada protokol class-aware yang sama, conf 0,50/IoU 0,50:

| Model | TP | FP | FN | Precision | Recall |
|---|---:|---:|---:|---:|---:|
| Sebelum | 558 | 122 | 123 | 82,06% | 81,94% |
| Background v1 | 515 | 159 | 166 | 76,41% | 75,62% |

Kandidat ini **ditolak untuk mengganti model demo**. Kaleng membaik precision-nya, tetapi PET lebih sering salah kelas dan recall kaca turun. [Report lengkap](../evaluations/background-v1-validation-20261020.json), [matriks kandidat](../evaluations/yolo-background-v1-val-conf050.png). Menambah negatif tidak otomatis menyelesaikan pertukaran kelas.

Script reproduksi (memerlukan environment training dan dataset lokal):

```powershell
.\.venv-ai-train\Scripts\python.exe ai/scripts/prepare_background_training.py
.\.venv-ai-train\Scripts\python.exe ai/scripts/train_background_candidate.py
.\.venv-ai-train\Scripts\python.exe ai/scripts/calibrate_yolo.py --model ai/artifacts/training/drinking-waste-background-v1/weights/best.pt --output ai/artifacts/calibration-background
.\.venv-ai-train\Scripts\python.exe ai/scripts/compare_background_candidate.py --candidate-cache ai/artifacts/calibration-background/validation-predictions.json
```

Preparation menolak output yang sudah ada agar hasil lama tidak tertimpa. Training menulis run terpisah dan tidak mengganti checkpoint utama. Gate yang ditetapkan sebelum evaluasi: FP turun minimal 25% pada batas 0,50 dengan recall turun paling banyak 3 poin persentase. Pemilihan dan pengujian kandidat ini memakai val; hasilnya bukan akurasi kamera Indonesia atau holdout dunia nyata.

## Konsistensi padding pada kandidat kedua

Kandidat shape-v2 juga dilatih dan selesai **20 epoch**, best epoch 18. Perubahan eksperimen: mosaic=0, degrees=30, flipud=0,2, scale=0,25, lr0=0,0001, inisialisasi dari checkpoint utama. Data/split sama dengan eksperimen negatif pertama. Ini eksperimen beberapa parameter, bukan bukti sebab-akibat satu augmentasi tertentu.

Hasil val training tidak langsung cocok dengan inference satu gambar. Probe yang hanya mengubah padding menemukan bahwa kandidat ini sangat sensitif terhadap bentuk input. `imgsz=640` dengan rect=True memakai padding adaptif, bukan selalu kanvas 640×640. Pilihan rect=False memaksa kanvas persegi. [Referensi argumen inference Ultralytics](https://docs.ultralytics.com/modes/predict/#inference-arguments).

Pada 681 gambar val, conf 0,50, scorer class-aware IoU 0,50:

| Model | Padding | TP | FP | FN | Precision | Recall |
|---|---|---:|---:|---:|---:|---:|
| Utama | Adaptif | 558 | 122 | 123 | 82,06% | 81,94% |
| Shape-v2 | Adaptif | 257 | 395 | 424 | 39,42% | 37,74% |
| Utama | Persegi | 559 | 135 | 122 | 80,55% | 82,09% |
| Shape-v2 | Persegi | 552 | 119 | 129 | 82,27% | 81,06% |

Padding saja membuat perbedaan besar pada kandidat shape-v2. Namun pada perbandingan **sama-sama persegi**, FP hanya turun sekitar 11,9%, belum target 25%. Model utama adaptif juga masih mempunyai recall sedikit lebih tinggi. Kandidat kedua tidak menggantikan model utama. Tidak ada klaim bahwa model baru sudah meningkatkan akurasi dunia nyata.

Laporan [adaptif](../evaluations/background-shape-adaptive-validation-20261020.json), [persegi](../evaluations/background-shape-square-validation-20261020.json), dan [matriks persegi](../evaluations/yolo-background-shape-square-val-conf050.png). Jumlah pada matriks Ultralytics memakai matching IoU 0,45, sesuai protokol sebelumnya; jangan dicampur dengan scorer IoU 0,50 pada tabel.

Runtime sekarang menyediakan `--input-shape adaptive|square` untuk detect, demo, benchmark, pipeline dan worker; env `AI_INPUT_SHAPE` juga tersedia. Default tetap adaptive untuk model utama yang sudah diukur. Square diberi suffix `-square640` pada model_version sehingga profil threshold adaptif tidak dipakai diam-diam untuk pipeline yang berbeda. Checksum tetap mengidentifikasi bobot, sedangkan mapping/profile harus ikut diperiksa oleh Go. Mode square yang belum memiliki profil terdaftar memakai default katalog dan tetap eksperimen.

```powershell
# Reproduksi training kedua:
.\.venv-ai-train\Scripts\python.exe ai/scripts/train_background_candidate.py --name drinking-waste-background-shape-v2 --epochs 20 --lr0 0.0001 --mosaic 0 --degrees 30 --flipud 0.2 --scale 0.25
# Kalibrasi/perbandingan dengan padding yang dinyatakan eksplisit:
.\.venv-ai-train\Scripts\python.exe ai/scripts/calibrate_yolo.py --square --model ai/artifacts/training/drinking-waste-background-shape-v2/weights/best.pt --output ai/artifacts/calibration-shape-square
# Mencoba kandidat kedua secara eksplisit, bukan model default:
.\.venv-ai\Scripts\python.exe -m wastenot_ai.cli demo --model ai/artifacts/training/drinking-waste-background-shape-v2/weights/best.pt --input-shape square --demo-guides --port 8095
```

Peningkatan model berikutnya membutuhkan variasi positif yang sesuai kesalahan teramati: kaleng dari atas/bawah, penyok, rebah, label hijau dan close-up; serta botol PET/kaca yang bentuknya mirip. Ini usulan pengumpulan berdasarkan audit visual, bukan dataset baru yang diklaim sudah tersedia. Holdout kamera dengan benda/sesi baru tetap diperlukan. Dua percobaan di atas menunjukkan mengapa menambah latar atau mengubah augmentasi saja tidak cukup untuk mengklaim peningkatan.

## Verifikasi pembaruan

183 tes Python lulus, 44/44 skenario fixture, 34 artifact kontrak cocok dengan generator, 16 pemeriksaan browser offline lulus. Satu pemeriksaan browser tambahan memakai Gemini langsung dengan seluruh kondisi opsional tetap UNKNOWN/null dan berhasil menghasilkan Reuse bersyarat tanpa error JavaScript. Deteksi square juga dijalankan pada model nyata; model_version dan profil default sesuai mode yang dipilih. Kamera fisik tidak dipakai untuk pengujian otomatis. Screenshot/laporan demo berada di `ai/artifacts/conditional-live-browser.*`.
