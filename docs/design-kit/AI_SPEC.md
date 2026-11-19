# WasteNot: Computer Vision dan Rekomendasi AI

Versi 1.2 · 28 September 2026 · Spesifikasi usulan, belum diimplementasikan

Pembaruan 20 Oktober 2026: prototipe YOLO–Gemini lokal tersedia. Keputusan terbaru pengguna mengganti rekomendasi tiga metode/alternatif menjadi satu label REDUCE/REUSE/RECYCLE/TRASH + alasan + sumber. Rincian kontrak dan cara mencoba ada pada [kontrak rekomendasi terbaru](https://github.com/waste-not-app/waste-not-ai/blob/main/docs/archive/RECOMMENDATION_CONTRACT.md). Integrasi Go/Android tetap pekerjaan lanjutan.

## 1. Sumber, scope, dan keputusan

Permintaan pengguna pada 28 September memperluas dokumentasi mengikuti [rencana teman tim](sources/AI_PLAN_ORIGINAL.md). Dokumen sumber adalah masukan produk, bukan instruksi untuk menjalankan deployment, mengunggah foto, atau mengaktifkan layanan. PRD PDF tetap sumber kebutuhan inti. Dokumen ini merinci tambahan AI dan menyelesaikan ambiguitas agar Android/backend memakai kontrak yang sama.

| Status | Keputusan |
|---|---|
| Kebutuhan dari rencana | CameraX, YOLO, confidence dan bounding box, koreksi objek, konteks, Waste Guide, LLM terstruktur, validasi, konfirmasi, input jumlah manual |
| Baseline rancangan tambahan | Capture satu foto per request; inference server Python privat di belakang Go API; satu objek dipilih untuk satu catatan |
| Tetap berlaku | Enum domain, jumlah integer gram/benda, ownership, idempotensi, statistik dari catatan tersimpan, 10 XP per log aktif |
| Belum diputuskan tim | Model/checkpoint final, kelas rilis, dataset berlisensi, provider LLM, hardware, biaya, target performa numerik |
| Ditunda | Real-time frame streaming, estimasi berat, batch save, klasifikasi kimia/kontaminasi otomatis, on-device inference, offline LLM |

Permintaan lanjutan pengguna: action utama di tengah navbar membuka kamera dalam bottom sheet, dengan tombol input manual. Lima tab tetap ada; action di tengah atas dock bukan destinasi keenam.

AI merupakan increment setelah alur core stabil. Jadwal empat minggu lama adalah baseline core, bukan janji menyelesaikan semua AI dalam waktu yang sama. Lihat [rencana implementasi](IMPLEMENTATION_PLAN.md).

## 2. Alur dan batas tanggung jawab

Keputusan integrasi 20 Oktober 2026: Python memiliki mapping, retrieval panduan, allowed methods, Gemini dan validasi evidence. Go memegang JWT, ownership, sesi/TTL, idempotensi/budget dan transaksi catatan. Interface privat terpisah untuk YOLO dan keputusan; Go tidak menyalin aturan AI. OpenAPI, mock dan contoh tersedia pada [kontrak HTTP v1](https://github.com/waste-not-app/waste-not-ai/blob/main/docs/AI_HTTP_CONTRACT.md). Endpoint Go/Android masih perlu diimplementasikan.

```mermaid
flowchart TD
    A[Action Catat/Scan pada dock] --> B{Camera bottom sheet}
    B --> M[Input manual]
    B --> C[CameraX: ambil dan tinjau foto]
    C --> D[Setujui kirim foto untuk scan]
    D --> E[Go API: validasi gambar dan autentikasi]
    E --> F[YOLO pada inference service privat]
    F --> G[Pilih satu objek dan koreksi material]
    G --> H[Isi kondisi dan niat penggunaan ulang]
    H --> I[Mapping server dan panduan terkurasi]
    I --> J[LLM terstruktur + validasi kebijakan]
    J --> K[Pengguna konfirmasi atau ubah metode]
    K --> M
    M --> N[Isi berat atau jumlah, tanggal, catatan]
    N --> O[Konfirmasi simpan melalui endpoint log]
    O --> P[Riwayat, statistik, dashboard, XP]
    E -. Gagal .-> M
    F -. Tidak dikenali .-> M
    I -. Panduan tidak tersedia .-> M
    J -. Gagal atau tidak valid .-> M
```

YOLO mengenali kelas visual, bukan menentukan material kimia, berat, keamanan, atau penerimaan fasilitas. Waste Guide menyediakan aturan yang ditinjau. LLM merangkum rekomendasi dari konteks tersebut. Pengguna mengonfirmasi tindakan yang benar-benar dilakukan. Rekomendasi untuk masa depan belum menjadi waste log.

Reduce berarti benda/berat yang dihindari. Memotret sampah yang sudah ada tidak membuktikan Reduce. Sesuai keputusan pengguna 20 Oktober 2026, Gemini memilih satu dari REDUCE/REUSE/RECYCLE/TRASH sesuai panduan. REDUCE memakai recommendation_scope=PREVENTION, sebagai pencegahan penggunaan berikutnya; lainnya HANDLING. Saran pencegahan tidak berarti aktivitas sudah dilakukan. Pengguna mencatat tindakan Reduce yang benar-benar dilakukan melalui manual dengan jumlah yang dihindari, sampai backend mendukung validasi provenance pencegahan.

## 3. Kamera dan siklus hidup

- Minta izin kamera setelah pengguna memilih scan. Ditolak: beri input manual; jika izin diblokir permanen, tawarkan pengaturan sistem tanpa prompt berulang.
- CameraX Preview + ImageCapture terikat lifecycle. Tutup kamera saat keluar/background; iterasi awal tidak menjalankan ImageAnalysis atau upload stream.
- Capture menghasilkan file sementara app-internal. Tampilkan preview dengan Ambil ulang, Kirim untuk scan, dan Batal. Izin kamera tidak dianggap persetujuan upload.
- Jelaskan sebelum upload: foto dikirim untuk pengenalan; tidak otomatis menjadi lampiran atau data latihan. Hindari memotret orang/dokumen pribadi.
- Normalisasi orientasi dan buang EXIF sebelum upload. Backend memvalidasi dan menormalisasi ulang; jangan percaya client.
- Batal sebelum kirim menghapus foto lokal. Batal setelah kirim membatalkan UI/request semampunya; server tetap menjalankan cleanup meskipun koneksi putus.
- Draf kategori/metode/jumlah bertahan sesuai core. Setelah process death, foto sementara yang hilang memunculkan Ambil ulang; jangan mengunggah ulang diam-diam. Logout membuang sesi AI dan foto sementara akun tersebut.

## 4. Deteksi dan mapping

YOLO11n adalah kandidat baseline dari rencana, bukan model yang sudah dipilih/diuji. Model pretrained COCO memiliki label umum seperti `bottle`; label itu tidak cukup untuk memastikan `plastic_bottle` versus `glass_bottle`. Daftar kelas harus dibaca dari artifact model aktual, bukan dibuat berdasarkan keinginan UI. Dokumentasi resmi: [YOLO11](https://docs.ultralytics.com/models/yolo11/) dan [COCO](https://docs.ultralytics.com/datasets/detect/coco/).

| Label dari model yang benar-benar mendukungnya | Kategori usulan | Kunci panduan | Ketentuan |
|---|---|---|---|
| bottle (umum) | null | null | Wajib pengguna memilih material; jangan otomatis PLASTIC |
| plastic_bottle | PLASTIC | pet_bottle | Hanya jika model dilatih dan dievaluasi untuk kelas ini |
| glass_bottle | GLASS | glass_bottle | Konfirmasi kondisi/pecah |
| aluminium_can | METAL | aluminium_can | Bukan semua objek silinder |
| cardboard / paper | PAPER | cardboard / paper | Konteks kotor/basah tetap dari pengguna |
| food_waste | ORGANIC | food_waste | Tidak mengidentifikasi kandungan kimia |
| battery / electronic_waste | ELECTRONIC | battery / electronic_waste | Special handling; flag bukan kategori baru |
| Label tak dikenal | null | null | Pilih manual; OTHER hanya pilihan eksplisit pengguna |

Mapping adalah tabel versioned milik modul Python: model_version + class_id → object_key + category nullable + guide_key nullable + special_handling. Go menyimpan snapshot hasil dan mengirim objek dari sesi miliknya ke interface keputusan privat. Client tidak dapat memaksakan snapshot/guide/confidence. Koreksi pengguna memakai katalog server; unknown/custom menuju manual bila tidak ada panduan cocok.

Bbox memakai `[x_min,y_min,x_max,y_max]` normalized 0..1 terhadap gambar setelah orientasi dibetulkan; x_min < x_max dan y_min < y_max. Model adapter mengonversi output runtime ke kontrak ini. Android menghitung ulang posisi pada crop/letterbox preview. Pilihan daftar dan bbox merujuk detection_id yang sama.

### Confidence dan multi-object

| Score kandidat awal | State | Perilaku |
|---|---|---|
| ≥0,70 | OFFERED | Boleh ditawarkan, tetap wajib konfirmasi |
| ≥0,50 dan <0,70 | UNCERTAIN | Teks belum yakin; koreksi atau scan ulang |
| <0,50 / tidak ada kandidat | MANUAL_REQUIRED | Tidak prefill kategori/metode; manual atau ulang |

Ambang adalah hipotesis pengujian, bukan akurasi terukur. Skor 0,94 ditulis “Skor deteksi 94%”, bukan “94% pasti benar” atau akurasi model. Kalibrasi dan ambang per kelas dievaluasi pada validation set; test set tetap terpisah. Satu foto boleh menghasilkan banyak bbox; pengguna memilih satu. Tidak menghitung benda dari banyak bbox, tidak menggandakan XP, tidak membuat batch log.

## 5. Konteks dan panduan

Kondisi bukan satu pilihan yang saling meniadakan. Gunakan tiga field: cleanliness CLEAN/DIRTY/UNKNOWN, integrity INTACT/DAMAGED/UNKNOWN, reusable YES/NO/UNKNOWN. `reuse_intention` boolean nullable terpisah. Default UNKNOWN/null; kamera tidak mengisi kondisi sebagai fakta.

Policy-04 menambahkan condition.cleanable=YES/NO/UNKNOWN dan condition.soiling=FOOD_DRINK/DUST/HAZARDOUS/UNKNOWN, serta risk_type=UNKNOWN/SHARP/CHEMICAL/BIOLOGICAL/ELECTRICAL/OTHER. Pada peralatan makan/minum, kotor biasa yang dikonfirmasi bisa dibersihkan dapat memenuhi REUSE dengan langkah mencuci dahulu, jika utuh, layak dan ingin dipakai kembali. DIRTY tidak otomatis diubah menjadi CLEAN. Centang risiko yang belum dijelaskan menghasilkan NEEDS_CONTEXT + missing_context sebelum memanggil Gemini. Metadata sumber tercatat pada registri, dengan penerbit, wilayah, tanggal pemeriksaan referensi dan batas cakupan; bukan persetujuan ahli lokal.

Retrieval awal memakai lookup object_key/guide_key dan filter category + publication_status=PUBLISHED. Tidak perlu vector database atau embeddings. Panduan harus memiliki version, reviewed_at, sumber, metode yang diizinkan, syarat konteks, steps, cautions, special_handling. Artikel draft dalam HTML lama tidak boleh menjadi knowledge produksi. Jika kosong/kedaluwarsa menurut kebijakan editorial, tampilkan UNAVAILABLE dan pilihan manual; jangan mengisi kekosongan dengan pengetahuan bebas LLM.

Rekomendasi mengacu pada snapshot panduan dan kondisi yang dikonfirmasi. Mengubah objek/kategori/kondisi membatalkan rekomendasi lama; request lama tidak boleh menimpa state baru. Perubahan metode manual tetap diperbolehkan dan dicatat sebagai koreksi pengguna.

## 6. LLM dan validasi

Provider berada di backend. Kirim hanya object_key, kategori, konteks, metode yang diizinkan, dan potongan panduan yang diperlukan. Jangan kirim foto, nama, email, lokasi, token, atau catatan pribadi ke LLM baseline. Jangan beri LLM tool database atau browsing bebas.

Output internal Gemini mempunyai tepat satu recommended_method wajib (REDUCE/REUSE/RECYCLE/TRASH), reason, special_handling, caution, dan evidence_ids; tidak memiliki alternative_method. Respons READY menambahkan recommendation_scope, source_urls dan steps dari evidence/panduan yang tervalidasi. special_handling adalah flag peringatan dan tidak menghilangkan metode READY. Server memvalidasi JSON/schema, batas panjang (reason 500, caution 500 karakter), enum, referensi evidence, dan kesesuaian aturan panduan. Prototipe merender alasan dari evidence pilihan, bukan prosa bebas model. JSON yang valid saja belum membuktikan rekomendasi benar. Teks panduan diperlakukan sebagai data; instruksi tersembunyi di dalamnya tidak boleh mengubah kebijakan sistem. Tidak memakai OCR/teks gambar sebagai instruksi.

Server menghitung allowed methods dari panduan dan konteks, lalu memeriksa satu hasil LLM terhadap subset itu. Evidence hanya boleh mendukung metode terpilih. recommendation_goal=AUTO/PREVENTION/HANDLING mengatur fokus; AUTO/HANDLING memilih penanganan saat ini, REDUCE hanya untuk PREVENTION yang dipilih pengguna. Kekurangan evidence tidak diubah menjadi pencegahan. recycling_access=YES/NO/UNKNOWN mencatat pemeriksaan penerima. Default AUTO/UNKNOWN. Tidak ada akses daur ulang tidak otomatis membolehkan TRASH tanpa evidence spesifik. Evidence ID harus berasal dari retrieval request tersebut; URL sumber disediakan server dari evidence, bukan URL baru buatan LLM. COMPOST tidak otomatis menjadi enum baru atau diam-diam dipetakan ke RECYCLE tanpa aturan editorial.

Prioritas validasi policy-04: tentukan risiko → ketersediaan panduan → klarifikasi konteks yang kurang → evidence sesuai risiko/kondisi → allowed methods → schema/evidence → satu label dan peringatan. Risiko yang tercakup tidak menghilangkan klasifikasi. Jika special_handling benar, hanya evidence risk_compatible=true yang boleh dipilih; model tidak boleh menghapus flag tersebut. Contoh: kaca pecah → TRASH dengan pelindung, baterai/elektronik/pisau/gunting → RECYCLE melalui pengelola khusus. Kontaminasi kimia/biologis tidak mengikuti aturan cuci/residu umum. Instruksi dan sumber mengikuti metode terpilih. Kelas tidak terdeteksi bukan bukti benda aman. Lihat [mutu panduan dan kasus cangkir](https://github.com/waste-not-app/waste-not-ai/blob/main/docs/archive/GUIDE_QUALITY.md).

Jika timeout, JSON invalid, evidence palsu, atau aturan tidak cocok, respons UNAVAILABLE dengan method null. Boleh tampilkan tautan panduan yang valid. Tidak ada auto-save atau fallback saran acak. Pilihan manual tetap melewati validasi domain biasa; tampilkan pengingat bahwa metode adalah tindakan yang dilakukan, bukan sertifikasi penanganan aman.

## 7. State dan konsistensi

`Idle → Permission → Preview → ReviewPhoto → Detecting → ReviewObject → Context → Recommending → ReviewRecommendation → LogDraft → Saving → Saved`.

Exit melalui Batal/Manual tersedia di semua tahap sebelum Saving. Tidak ada retry otomatis upload/inference berulang. Tombol ulang mengirim aksi eksplisit; hasil request lama diabaikan dengan request ID/generation. Loading dapat dibatalkan dan tidak memiliki progres persentase palsu. Timeout tidak menghapus draf. Setelah rekomendasi expired, kategori/metode yang sudah dikonfirmasi tetap bisa disimpan sebagai manual setelah metadata AI dilepas secara eksplisit.

AI session dan recommendation tidak menambah log/statistik/XP. Hanya POST waste-logs yang sukses menambah catatan; replay mengikuti client_request_id core. Retry model boleh menambah biaya inference, sehingga memakai dedup request ID dan pembatasan akun. Detail/edit log menunjukkan data final pengguna. Metadata awal AI adalah provenance, tidak ditulis ulang seolah model menyarankan hasil koreksi belakangan.

## 8. Privasi, retensi, dan operasi

Keputusan baseline usulan yang harus diwujudkan sebelum pilot:

| Data | Retensi / batas |
|---|---|
| Foto lokal scan | Hapus saat keluar alur/logout atau paling lambat cleanup 1 jam; bukan galeri pengguna |
| Foto inference server | Memori/tmp ephemeral tanpa backup; hapus saat selesai/gagal; scavenger batas 15 menit untuk crash |
| Sesi deteksi/rekomendasi sementara | TTL 24 jam; user-scoped; hapus job berkala |
| Metadata AI yang terhubung log | Umur log; hapus bersama data log sesuai kebijakan retensi core, akses langsung dicabut saat log dihapus |
| Telemetri operasional | 7 hari untuk pilot, tanpa raw image/prompt/notes/token; agregat evaluasi dianonimkan |
| Foto lampiran | Jalur opt-in terpisah /photos dan aturan core; scan tidak mengaktifkannya |
| Dataset training | Tidak mengambil foto pengguna otomatis; dataset terkurasi berlisensi/persetujuan terpisah |

Batasi file JPEG/PNG ≤5 MiB, decoded ≤12 megapiksel, sisi ≤4096 px; tolak malformed/decompression bomb. Angka ini batas rancangan API, bukan limit model. Resize input model dilakukan setelah validasi. Semua endpoint AI butuh sesi; internal service tidak diekspos publik. Akun lain menerima 404 untuk detection/recommendation milik pengguna lain.

Baseline timeout usulan: inference internal 10 detik, LLM 15 detik, deadline API 20 detik per request; timeout client 25 detik. Queue inference bounded; overload menghasilkan 429/503, bukan menumpuk gambar. Rate limit awal usulan 10 scan/menit/akun dan 30 rekomendasi/jam/akun; batas biaya harian per lingkungan wajib diisi tim. Jangan memanggil LLM setiap frame. Semua angka harus dituning dari pengukuran; tidak dipresentasikan sebagai latency aktual.

LLM provider harus mendukung kebijakan retensi yang disepakati tim; konfigurasi sebelum pilot. Simpan secret server-side. Pemilihan model YOLO juga memerlukan pencatatan lisensi kode, bobot dan dataset; [halaman lisensi Ultralytics](https://www.ultralytics.com/license) menyediakan opsi AGPL-3.0 dan Enterprise. Belum ada keputusan lisensi/deployment komersial dalam kit ini.

## 9. Dataset, evaluasi, dan release gate

Mulai dari pipeline pretrained dengan label asli; fine-tune hanya setelah taksonomi dan anotasi disepakati. Setiap artifact mempunyai model_version, checksum, daftar label, preprocessing, ukuran input, mapping_version dan provenance dataset. Pisahkan train/validation/test berdasarkan objek fisik/sesi pengambilan, bukan frame acak yang hampir sama. Audit duplikat, lisensi, ketimpangan kelas, dan background leakage. Data pengguna yang dikoreksi tidak otomatis menjadi label training benar.

Laporan harus memuat jumlah gambar/instance per split dan kelas, precision/recall per kelas, confusion matrix, mAP@0.5 dan mAP@0.5:0.95, false negative special waste, unknown rejection, confidence calibration, serta ukuran sampel. Uji cahaya rendah, objek kecil/tertutup, multi-object, background rumit, material mirip, perangkat/lokasi berbeda. Angka aggregate tidak cukup untuk menyetujui kelas berisiko.

Ukur upload/preprocess/inference/retrieval/LLM/end-to-end secara terpisah, p50/p95, hardware, versi runtime, ukuran foto dan koneksi. Catat acceptance/correction/fallback rate per versi serta waktu pengguna menyelesaikan log dibanding manual. Acceptance pengguna bukan ground truth akurasi. Target numerik kualitas/biaya/latency ditetapkan setelah baseline, sebelum pilot; tabel hasil tetap BELUM DIUKUR sampai ada bukti.

Release AI hanya jika taxonomy tersedia, mapping diuji, panduan PUBLISHED nyata, lisensi dicatat, privasi/cleanup dibuktikan, fallback manual berjalan, isolasi akun/idempotensi lulus, dan penanggung jawab menyetujui hasil holdout. Jika gagal, matikan capability AI tanpa mematikan tracking core. Model update memiliki evaluasi regresi dan rollback artifact+mapping; jangan mengganti bobot tanpa versi.

## 10. Referensi dan traceability

- Rencana tim §1–8 → pipeline, scope, confidence, validasi, special handling di §1–6 dokumen ini.
- Rencana tim §9–13 → [ARCHITECTURE](ARCHITECTURE.md) dan [API](API.md), termasuk endpoint detection yang belum dirinci sumber.
- Rencana tim §14–19 → dataset/evaluasi/privasi di §8–9 dan acceptance [IMPLEMENTATION_PLAN](IMPLEMENTATION_PLAN.md).
- Rencana tim §20–23 → increment AI, optimasi ditunda, dan milestone setelah core.
- [CameraX architecture](https://developer.android.com/media/camera/camerax/architecture) dan [Image capture](https://developer.android.com/media/camera/camerax/take-photo): lifecycle dan capture, diperiksa 28 September 2026.
- [YOLO11](https://docs.ultralytics.com/models/yolo11/), [COCO](https://docs.ultralytics.com/datasets/detect/coco/), [lisensi](https://www.ultralytics.com/license): kandidat model, taksonomi pretrained, dan opsi lisensi; diperiksa 28 September 2026. Tidak mengunci versi runtime atau menjanjikan akurasi WasteNot.

### Pembaruan kualitas pilot, 20 Oktober 2026

Kontrak Python v1 menambahkan review_profile dan review_thresholds/review_reasons/ambiguous_with per kandidat, serta condition.former_contents=FOOD_DRINK/CHEMICAL/OTHER/UNKNOWN. Band server mengutamakan profil checkpoint; tabel threshold global adalah default COCO. Kandidat empat kelas SHA af1446f33338... memakai dw-val-01: can/HDPE OFFERED >=0,70; PET >=0,725; kaca selalu UNCERTAIN; beda kelas dengan overlap IoU>=0,65 tetap terlihat dan UNCERTAIN. Semua kandidat memerlukan konfirmasi pengguna.

Go mengirim model_sha256 dari snapshot privat pada selection agar aturan bisa diperiksa ulang; tidak menerima provenance tersebut dari frontend. Pilot RECYCLE memerlukan recycling_access=YES; HDPE memerlukan former_contents=FOOD_DRINK. UNKNOWN meminta konteks; tidak ditebak menjadi Trash/Reduce. Semua 69 panduan masih DRAFT. Rincian, metrik val beserta tradeoff, evaluasi Gemini dan perintah reproduksi: [mutu deteksi](https://github.com/waste-not-app/waste-not-ai/blob/main/docs/archive/DETECTION_QUALITY.md), [mutu rekomendasi](https://github.com/waste-not-app/waste-not-ai/blob/main/docs/archive/RECOMMENDATION_EVALUATION.md). OpenAPI pada ai/contracts/v1 merupakan acuan DTO yang diregenerasi.

### Saran bersyarat saat kondisi kosong, policy-06

Konteks biasa yang belum diisi tetap UNKNOWN/null. allow_conditional default true mengizinkan READY dengan recommendation_basis=CONDITIONAL dan conditions_to_verify; false mempertahankan mode ketat NEEDS_CONTEXT. UI menampilkan saran sementara dan syarat, tanpa mengisi data kosong atau menyimpan otomatis. Risiko ambigu, kontaminasi berbahaya dan riwayat isi HDPE tetap dibatasi. Lihat [perilaku terbaru, reproduksi dan hasil pengujian](https://github.com/waste-not-app/waste-not-ai/blob/main/docs/archive/CONDITIONAL_AND_BACKGROUND.md).
