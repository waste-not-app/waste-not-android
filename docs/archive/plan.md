# WasteNot AI-Assisted Development Plan

## 1. Ringkasan

Dokumen ini merupakan rencana pengembangan **WasteNot** dengan menambahkan fitur **AI-assisted waste detection and recommendation** di atas MVP yang sudah didefinisikan pada PRD.

Pada PRD awal, proses pencatatan sampah dilakukan secara manual dan **AI image classification belum termasuk MVP**. Pengembangan pada dokumen ini menambahkan alur baru agar pengguna dapat memindai objek melalui kamera, lalu sistem membantu mengenali jenis objek dan memberikan rekomendasi metode pengelolaan yang sesuai.

Alur utama yang direncanakan:

```text
Camera
  ↓
YOLO Object Detection
  ↓
Detected Object + Confidence
  ↓
Waste Mapping
  ↓
Waste Guide / Knowledge Base Retrieval
  ↓
LLM Recommendation
  ↓
Recommendation Validation
  ↓
User Confirmation
  ↓
Input Weight / Count
  ↓
Save Waste Log
```

AI berfungsi sebagai **asisten**, bukan pengambil keputusan final. Pengguna tetap harus mengonfirmasi hasil deteksi dan rekomendasi sebelum aktivitas disimpan.

---

## 2. Tujuan Pengembangan

Tujuan utama fitur AI adalah mengurangi jumlah input manual dan membuat proses pencatatan sampah menjadi lebih cepat serta lebih mudah dipahami.

Target pengembangan:

1. Mengenali objek sampah dari kamera menggunakan object detection.
2. Memetakan hasil deteksi ke kategori WasteNot.
3. Memberikan rekomendasi metode pengelolaan berdasarkan objek, kondisi, dan knowledge base.
4. Memberikan alasan singkat atas rekomendasi.
5. Mempertahankan keputusan akhir di tangan pengguna.
6. Mempertahankan input manual sebagai fallback.
7. Mengintegrasikan hasil AI ke fitur tracking, riwayat, dashboard, statistik, dan XP yang sudah ada pada WasteNot.

---

## 3. Scope

### 3.1 In Scope

Fitur yang termasuk dalam pengembangan AI:

- Scan sampah melalui kamera.
- Object detection menggunakan pretrained/fine-tuned YOLO.
- Bounding box dan confidence score.
- Mapping class YOLO ke kategori WasteNot.
- Pengambilan knowledge dari Waste Guide.
- Rekomendasi metode menggunakan LLM.
- Output LLM dalam format terstruktur.
- Validasi rekomendasi ke enum WasteNot.
- Penanganan special waste.
- Konfirmasi user sebelum penyimpanan.
- Input berat atau jumlah benda setelah rekomendasi.
- Fallback ke input manual.
- Logging hasil inference untuk evaluasi teknis, tanpa menyimpan data sensitif yang tidak diperlukan.

### 3.2 Out of Scope Awal

Fitur berikut tidak menjadi prioritas pada iterasi pertama:

- Estimasi berat otomatis dari kamera.
- Deteksi material kimia dari gambar.
- Deteksi tingkat kontaminasi secara otomatis dengan akurasi tinggi.
- Fully autonomous waste disposal decision.
- Chatbot umum tanpa knowledge base.
- Auto-save tanpa konfirmasi pengguna.
- Deteksi lokasi recycling facility secara real-time.
- Model AI yang dilatih dari nol.

---

## 4. Prinsip Desain Sistem

### 4.1 AI sebagai Decision Support

AI tidak boleh dianggap selalu benar. Sistem hanya memberikan rekomendasi.

```text
AI Recommendation ≠ Final Decision
```

Final decision tetap berasal dari pengguna.

### 4.2 Pisahkan Perception dan Recommendation

YOLO dan LLM memiliki peran berbeda.

**YOLO** menjawab:

> "Objek apa yang terlihat?"

**LLM** menjawab:

> "Berdasarkan objek, kondisi, dan panduan yang tersedia, metode apa yang paling sesuai?"

### 4.3 Knowledge-Grounded Recommendation

LLM tidak boleh menjadi satu-satunya sumber pengetahuan. Waste Guide tetap menjadi sumber informasi utama.

Arsitektur yang digunakan:

```text
YOLO Detection
      ↓
Waste Guide Retrieval
      ↓
LLM Recommendation
```

### 4.4 Manual Fallback

Pengguna tetap dapat memilih:

```text
[ Scan Sampah ]

      atau

[ Input Manual ]
```

Jika AI gagal, aplikasi tetap dapat digunakan sepenuhnya.

---

## 5. User Flow

## 5.1 Flow Utama Scan Sampah

```text
Beranda
  ↓
Catat Aktivitas
  ↓
Pilih "Scan Sampah"
  ↓
Camera Preview
  ↓
Ambil Foto / Deteksi
  ↓
YOLO Inference
  ↓
Hasil Deteksi
  ↓
Pilih Objek Jika Ada Beberapa
  ↓
Konfirmasi / Koreksi Objek
  ↓
Isi Kondisi Objek
  ↓
Retrieve Waste Guide
  ↓
LLM Recommendation
  ↓
Tampilkan Method + Reason
  ↓
User Konfirmasi / Ubah
  ↓
Input Weight / Count
  ↓
Simpan
  ↓
Waste Log Tersimpan
```

---

## 5.2 Flow Ketika YOLO Tidak Yakin

Contoh:

```text
Detected:
Plastic Bottle
Confidence: 54%
```

Jika confidence di bawah threshold:

```text
Kami belum yakin dengan objek ini.

[ Scan Ulang ]
[ Pilih Objek Manual ]
[ Input Manual ]
```

Threshold awal yang diusulkan:

```text
confidence >= 0.70 → dapat langsung ditawarkan
0.50–0.69         → tampilkan sebagai hasil tidak pasti
< 0.50            → arahkan ke manual selection
```

Nilai threshold wajib divalidasi berdasarkan hasil pengujian model.

---

## 5.3 Flow Multi-Object

Jika kamera mendeteksi beberapa objek:

```text
Detected Objects:

1. Plastic Bottle — 94%
2. Aluminium Can — 89%
3. Cardboard — 91%
```

Pengguna dapat memilih satu objek untuk dicatat terlebih dahulu.

Iterasi lanjutan dapat mendukung batch logging beberapa objek sekaligus.

---

## 6. AI Pipeline

## 6.1 Stage 1 — Camera Input

Android menggunakan CameraX untuk menyediakan:

- camera preview;
- capture image;
- optional real-time frame analysis;
- lifecycle-aware camera handling.

Output:

```text
Image / Camera Frame
```

---

## 6.2 Stage 2 — YOLO Object Detection

Model yang direkomendasikan sebagai baseline:

```text
YOLO Nano Variant
```

Contoh opsi:

- YOLO11n
- model YOLO nano lain yang kompatibel dengan target deployment

Model pretrained digunakan sebagai starting point, lalu dapat di-fine-tune menggunakan waste dataset.

Output:

```json
{
  "detections": [
    {
      "class": "plastic_bottle",
      "confidence": 0.94,
      "bbox": [120, 82, 420, 610]
    }
  ]
}
```

---

## 6.3 Stage 3 — Waste Mapping

Class dari YOLO dipetakan ke domain WasteNot.

Contoh:

| YOLO Class | WasteNot Category | Guide Key |
|---|---|---|
| plastic_bottle | PLASTIC | pet_bottle |
| glass_bottle | GLASS | glass_bottle |
| cardboard | PAPER | cardboard |
| aluminium_can | METAL | aluminium_can |
| banana_peel | ORGANIC | food_waste |
| battery | ELECTRONIC | battery |

Contoh object hasil mapping:

```json
{
  "detected_object": "plastic_bottle",
  "category": "PLASTIC",
  "guide_key": "pet_bottle",
  "confidence": 0.94
}
```

---

## 6.4 Stage 4 — Additional Context

Karena objek yang sama dapat memiliki metode berbeda, pengguna dapat diminta memberi konteks sederhana.

Contoh:

```text
Kondisi barang:

○ Bersih
○ Kotor
○ Rusak
○ Masih layak digunakan
```

Optional context:

```text
Apakah ingin menggunakan kembali barang ini?

○ Ya
○ Tidak
```

Tujuannya bukan membuat form panjang, tetapi memberikan informasi yang tidak dapat diketahui dengan aman hanya dari gambar.

---

## 6.5 Stage 5 — Waste Guide Retrieval

Sistem mengambil knowledge berdasarkan `guide_key`.

Data yang dapat digunakan:

```json
{
  "name": "Botol PET",
  "category": "PLASTIC",
  "recommended_methods": ["REUSE", "RECYCLE"],
  "summary": "...",
  "steps": ["..."],
  "avoid": ["..."],
  "source_url": "...",
  "reviewed_at": "..."
}
```

Waste Guide berfungsi sebagai knowledge base yang dikurasi.

---

## 6.6 Stage 6 — LLM Recommendation

LLM menerima:

- hasil object detection;
- confidence;
- category;
- kondisi dari user;
- Waste Guide content;
- daftar method yang valid.

Contoh input terstruktur:

```json
{
  "object": "plastic_bottle",
  "category": "PLASTIC",
  "condition": "clean",
  "reuse_intention": false,
  "allowed_methods": [
    "REDUCE",
    "REUSE",
    "RECYCLE",
    "TRASH"
  ],
  "knowledge": {
    "recommended_methods": ["REUSE", "RECYCLE"],
    "notes": "PET bottle can be recycled if accepted by the local facility."
  }
}
```

Output wajib structured.

```json
{
  "recommended_method": "RECYCLE",
  "reason": "Botol PET dalam kondisi bersih dapat diarahkan ke daur ulang jika fasilitas setempat menerimanya.",
  "alternative_method": "REUSE",
  "special_handling": false,
  "caution": "Periksa aturan fasilitas daur ulang setempat."
}
```

---

## 7. Recommendation Validation Layer

Output LLM tidak langsung digunakan.

Backend harus memvalidasi:

```text
recommended_method ∈ {
    REDUCE,
    REUSE,
    RECYCLE,
    TRASH
}
```

Jika tidak valid:

```text
method = null
require_user_selection = true
```

Contoh:

Jika LLM menghasilkan:

```text
COMPOST
```

backend tidak menyimpan `COMPOST` sebagai method karena tidak termasuk domain WasteNot.

Sistem dapat menerjemahkannya menjadi penjelasan tambahan, tetapi user tetap harus memilih salah satu method resmi.

---

## 8. Special Waste Handling

Jenis tertentu tidak boleh diberi rekomendasi penanganan bebas.

Contoh:

- battery;
- electronic waste;
- sharp object;
- hazardous material.

Output:

```json
{
  "recommended_method": null,
  "special_handling": true,
  "reason": "Jenis ini memerlukan fasilitas atau petugas khusus.",
  "caution": "Jangan dibakar, dibongkar, atau dibuang bersama sampah biasa tanpa panduan fasilitas setempat."
}
```

UI:

```text
⚠ Penanganan Khusus

Baterai tidak disarankan diproses sendiri.
Gunakan fasilitas resmi yang menerima limbah jenis ini.

[ Pilih Metode Manual ]
[ Lihat Panduan ]
```

---

## 9. Android Architecture

Arsitektur Android tetap mengikuti prinsip PRD:

```text
UI
↓
ViewModel
↓
Repository
↓
Data Sources
```

Pengembangan AI menambahkan:

```text
Compose UI
   ↓
ScanViewModel
   ↓
WasteDetectionRepository
   ├── Camera / Image Input
   ├── YOLO Inference
   ├── Waste Mapping
   └── Backend Recommendation API
                        ↓
                 Waste Guide + LLM
```

Suggested module structure:

```text
app/

feature/
  home/
  tracking/
  scan/
  history/
  guide/
  statistics/
  profile/

data/
  auth/
  waste/
  guide/
  ai/

domain/
  model/
  repository/
  usecase/

core/
  network/
  database/
  camera/
  ai/
  ui/
```

---

## 10. Backend Architecture

Suggested architecture:

```text
Android App
    │
    │ REST API
    ▼
Go Backend
    │
    ├── Auth Service
    ├── Waste Log Service
    ├── Waste Guide Service
    ├── AI Recommendation Service
    │      │
    │      ├── Knowledge Retrieval
    │      └── LLM Provider
    │
    └── PostgreSQL
```

YOLO dapat ditempatkan dalam dua pendekatan.

### Option A — On-device

```text
Android
 └── YOLO inference
```

Kelebihan:

- latency rendah;
- dapat berjalan tanpa mengirim gambar ke server;
- privacy lebih baik.

Kekurangan:

- optimasi model lebih kompleks;
- resource device terbatas.

### Option B — AI Inference Service

```text
Android
   ↓ image
Inference Service
   ↓
YOLO
```

Kelebihan:

- model lebih mudah di-update;
- compute dapat dipusatkan.

Kekurangan:

- membutuhkan jaringan;
- upload image menambah latency;
- perlu perhatian terhadap privasi.

### Rekomendasi Iterasi Awal

Untuk prototype akademik:

```text
Camera → Backend/Inference Service → YOLO
```

Untuk pengembangan mobile lebih matang:

```text
Camera → On-device YOLO
```

LLM recommendation tetap idealnya melalui backend agar API key dan knowledge retrieval tidak ditempatkan di aplikasi Android.

---

## 11. Proposed API

### 11.1 AI Recommendation

```http
POST /api/v1/ai/recommendation
```

Request:

```json
{
  "detected_object": "plastic_bottle",
  "confidence": 0.94,
  "category": "PLASTIC",
  "guide_key": "pet_bottle",
  "condition": "clean",
  "reuse_intention": false
}
```

Response:

```json
{
  "recommended_method": "RECYCLE",
  "alternative_method": "REUSE",
  "reason": "Botol PET bersih dapat diarahkan ke daur ulang apabila fasilitas setempat menerimanya.",
  "special_handling": false,
  "caution": "Periksa aturan fasilitas setempat.",
  "knowledge_source": "pet_bottle",
  "request_id": "..."
}
```

---

## 12. UI Proposal

### 12.1 Catat Aktivitas

```text
┌─────────────────────────────┐
│      Catat Aktivitas        │
│                             │
│   ┌─────────────────────┐   │
│   │   📷 Scan Sampah     │   │
│   └─────────────────────┘   │
│                             │
│             atau            │
│                             │
│   ┌─────────────────────┐   │
│   │   ✏ Input Manual     │   │
│   └─────────────────────┘   │
└─────────────────────────────┘
```

### 12.2 Detection Result

```text
┌─────────────────────────────┐
│ Hasil Scan                  │
│                             │
│ Plastic Bottle             │
│ Confidence 94%             │
│                             │
│ Category                   │
│ Plastic                    │
│                             │
│ Apakah hasil ini benar?    │
│                             │
│ [ Ubah ]     [ Benar ]     │
└─────────────────────────────┘
```

### 12.3 Recommendation

```text
┌─────────────────────────────┐
│ Rekomendasi                 │
│                             │
│ ♻ RECYCLE                   │
│                             │
│ Botol PET dalam kondisi    │
│ bersih dapat diarahkan ke  │
│ daur ulang jika fasilitas │
│ menerima jenis tersebut.   │
│                             │
│ Alternatif: REUSE          │
│                             │
│ [ Ubah ]   [ Gunakan ]     │
└─────────────────────────────┘
```

---

## 13. Waste Data Model Extension

Waste log tetap menggunakan model utama PRD, tetapi dapat ditambah metadata AI.

Contoh:

```text
waste_logs
----------
id
user_id
category
method
quantity_kind
weight_g
item_count
activity_date
notes
created_at
updated_at
```

Optional AI metadata:

```text
ai_detection_metadata
---------------------
id
waste_log_id
model_name
model_version
detected_class
confidence
recommended_method
recommendation_version
user_accepted_detection
user_accepted_recommendation
created_at
```

Metadata AI tidak digunakan sebagai sumber utama statistik.

Statistik tetap dihitung berdasarkan waste log yang telah dikonfirmasi pengguna.

---

## 14. Dataset Strategy

### Phase 1

Gunakan pretrained YOLO untuk prototype awal.

Tujuan:

- memvalidasi pipeline kamera;
- menguji inference;
- menguji UI scan;
- menguji integrasi YOLO → backend → LLM.

### Phase 2

Fine-tune menggunakan custom waste dataset.

Target class awal disarankan tidak terlalu banyak.

Contoh:

```text
plastic_bottle
glass_bottle
plastic_cup
aluminium_can
cardboard
paper
food_waste
battery
electronic_waste
```

Jumlah class dapat diperluas setelah baseline stabil.

---

## 15. LLM Strategy

LLM digunakan hanya untuk recommendation layer.

### Input

- detected object;
- WasteNot category;
- condition;
- user intent;
- Waste Guide context;
- allowed methods.

### Output

Wajib JSON terstruktur.

### Constraint

LLM tidak boleh:

- menciptakan method baru;
- menyatakan hasil sebagai kepastian mutlak;
- memberikan instruksi berbahaya;
- membuat klaim bahwa material pasti diterima fasilitas lokal tanpa data;
- langsung mengubah database.

---

## 16. Failure Handling

### YOLO Failure

```text
Tidak ada objek ditemukan.

[ Scan Ulang ]
[ Input Manual ]
```

### Low Confidence

```text
Kami belum yakin dengan objek ini.

[ Pilih Objek ]
[ Scan Ulang ]
```

### Recommendation Service Failure

```text
Rekomendasi otomatis sedang tidak tersedia.

Kamu tetap dapat memilih metode secara manual.

[ Pilih Metode ]
```

### Network Failure

Draf tetap dipertahankan sesuai prinsip PRD.

Tidak boleh membuat log ganda saat retry.

---

## 17. Privacy Consideration

Jika inference dilakukan melalui server:

- gambar hanya dikirim setelah user melakukan scan;
- image tidak disimpan permanen kecuali fitur foto log diaktifkan;
- image inference tidak digunakan untuk kebutuhan lain tanpa persetujuan;
- log inference tidak menyimpan image raw secara default;
- authentication tetap diperlukan untuk endpoint personal recommendation jika mengakses konteks user.

Untuk deployment jangka panjang, on-device inference menjadi opsi yang lebih baik untuk mengurangi pengiriman gambar.

---

## 18. Testing Plan

## 18.1 YOLO Testing

Metrics:

- precision;
- recall;
- mAP;
- inference latency;
- confidence distribution.

Test scenario:

- satu objek;
- banyak objek;
- background kompleks;
- cahaya rendah;
- objek sebagian tertutup;
- class yang tidak dikenal.

---

## 18.2 Recommendation Testing

Gunakan test case tetap.

Contoh:

```text
Object: plastic bottle
Condition: clean
Expected allowed recommendation:
REUSE atau RECYCLE
```

```text
Object: battery
Expected:
special_handling = true
```

Validasi:

- output JSON valid;
- method termasuk enum;
- tidak ada unsafe instruction;
- recommendation konsisten dengan knowledge source.

---

## 18.3 End-to-End Testing

Scenario:

```text
Camera
→ Detect
→ Confirm Object
→ Retrieve Guide
→ LLM Recommendation
→ Confirm Method
→ Input Quantity
→ Save
→ History
→ Dashboard Updated
→ Statistics Updated
→ XP Updated
```

---

## 19. Acceptance Criteria

AI extension dianggap berhasil jika:

- [ ] User dapat membuka scanner dari Catat Aktivitas.
- [ ] Kamera dapat menangkap atau menganalisis objek.
- [ ] YOLO mengembalikan class dan confidence.
- [ ] Deteksi dapat dikoreksi user.
- [ ] Class dipetakan ke kategori WasteNot.
- [ ] Waste Guide dapat diambil berdasarkan hasil mapping.
- [ ] LLM menghasilkan recommendation dalam format terstruktur.
- [ ] Recommendation method hanya REDUCE/REUSE/RECYCLE/TRASH atau null untuk special handling.
- [ ] Special waste mendapatkan warning yang sesuai.
- [ ] User dapat menolak rekomendasi AI.
- [ ] User dapat memilih method manual.
- [ ] User tetap mengisi weight/count secara eksplisit.
- [ ] Save menghasilkan satu waste log saja.
- [ ] Dashboard/statistik menggunakan data setelah user confirmation.
- [ ] Kegagalan YOLO/LLM tidak menghalangi input manual.
- [ ] Timeout/retry tidak menghasilkan duplicate waste log.

---

## 20. Suggested Development Roadmap

### Phase 1 — Core WasteNot

Pastikan fitur inti PRD stabil:

```text
Auth
Tracking
History
Waste Guide
Dashboard
Statistics
```

### Phase 2 — Camera + YOLO Prototype

```text
CameraX
↓
YOLO inference
↓
Detection Result UI
```

Target:

- scan satu objek;
- tampilkan class;
- tampilkan confidence;
- user confirmation.

### Phase 3 — Waste Mapping

```text
YOLO Class
↓
Waste Category
↓
Guide Key
```

### Phase 4 — Waste Guide Retrieval

```text
Guide Key
↓
Waste Guide
```

### Phase 5 — LLM Recommendation

```text
Detection
+
Waste Guide
+
Context
↓
LLM
↓
Method Recommendation
```

### Phase 6 — Full Integration

```text
Scan
↓
Recommendation
↓
User Confirmation
↓
Waste Log
↓
Dashboard / History / Statistics
```

### Phase 7 — Fine-Tuning

Fine-tune YOLO menggunakan waste-specific dataset.

Target:

- meningkatkan class accuracy;
- mengurangi false detection;
- memperluas jenis sampah.

### Phase 8 — Optimization

Optional:

- on-device inference;
- quantized model;
- real-time detection;
- batch detection;
- offline scan support.

---

## 21. Suggested MVP AI Version

Agar scope tetap realistis, versi AI pertama disarankan hanya memiliki:

```text
Camera Scan
+
YOLO Detection
+
Single Object Selection
+
Waste Guide Retrieval
+
LLM Recommendation
+
User Confirmation
+
Manual Weight/Count
```

Belum perlu:

```text
real-time multi-object tracking
weight estimation
automatic contamination detection
fully offline LLM
```

---

## 22. Final Architecture

```text
                    ┌───────────────┐
                    │    Camera     │
                    └───────┬───────┘
                            │
                            ▼
                    ┌───────────────┐
                    │ YOLO Detector │
                    └───────┬───────┘
                            │
                  Object + Confidence
                            │
                            ▼
                    ┌───────────────┐
                    │ Waste Mapper  │
                    └───────┬───────┘
                            │
                            ▼
                    ┌───────────────┐
                    │ Waste Guide   │
                    │ Retrieval     │
                    └───────┬───────┘
                            │
                    Knowledge Context
                            │
                            ▼
                    ┌───────────────┐
                    │      LLM      │
                    │ Recommendation│
                    └───────┬───────┘
                            │
                   Method + Explanation
                            │
                            ▼
                    ┌───────────────┐
                    │ Validation    │
                    │ Layer         │
                    └───────┬───────┘
                            │
                            ▼
                    ┌───────────────┐
                    │ User Confirm  │
                    └───────┬───────┘
                            │
                    Weight / Count
                            │
                            ▼
                    ┌───────────────┐
                    │  Waste Log    │
                    └───────┬───────┘
                            │
          ┌─────────────────┼─────────────────┐
          ▼                 ▼                 ▼
       History          Dashboard        Statistics
```

---

## 23. Kesimpulan

WasteNot dikembangkan dari aplikasi waste tracking menjadi **AI-assisted waste management application**.

Pembagian peran sistem:

```text
YOLO
= mengenali objek

Waste Guide
= menyediakan knowledge terverifikasi

LLM
= menghasilkan rekomendasi berdasarkan konteks

User
= mengambil keputusan akhir

WasteNot
= mencatat dan mengevaluasi kebiasaan
```

Prinsip utama pengembangan adalah:

```text
AI membantu pengguna,
bukan menggantikan keputusan pengguna.
```

Dengan struktur ini, fitur AI dapat ditambahkan tanpa merusak desain dasar WasteNot dan tetap kompatibel dengan tracking, history, dashboard, statistics, serta gamification yang sudah dirancang pada PRD.
