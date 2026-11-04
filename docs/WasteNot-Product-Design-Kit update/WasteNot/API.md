# WasteNot: Data Model dan Kontrak API

Versi 1.2 · 28 September 2026 · Core + AI extension · Rancangan implementasi

Pembaruan AI 20 Oktober 2026: satu label REDUCE/REUSE/RECYCLE/TRASH, alasan dan sumber menggantikan rekomendasi dengan alternatif. Lihat [kontrak empat metode](../../../ai/docs/RECOMMENDATION_CONTRACT.md) untuk perilaku prototipe dan perubahan integrasi yang belum diimplementasikan pada Go/Android.

Dokumen ini menetapkan kontrak usulan Android dan backend, melengkapi draft endpoint halaman 4 PDF. Aturan domain ada di [PRD.md](PRD.md). Prefix `/v1` merupakan keputusan rancangan. Contoh menggunakan data simulasi, bukan akun atau endpoint nyata.

## 1. Konvensi

- HTTPS untuk lingkungan bersama. Base URL disediakan konfigurasi build; jangan hardcode alamat produksi di composable.
- JSON UTF-8, `snake_case`, UUID sebagai string, timestamp RFC 3339 UTC, tanggal aktivitas `YYYY-MM-DD`.
- Autentikasi `Authorization: Bearer <access_token>`. Server mengambil user dari token, tidak dari body/query.
- Semua endpoint selain register/login/refresh dan health memerlukan autentikasi pada baseline ini.
- Respons sukses memakai `data`; daftar menambah `page`; error memakai `error` dan `request_id`.
- Angka berat disimpan integer gram; jumlah benda integer. Persentase dihitung server dengan presisi penuh dan ditampilkan client sesuai PRD.
- `created_at` dan `updated_at` milik server. `activity_date` milik pengguna dalam batas yang diizinkan.
- Value enum API berbahasa Inggris kapital, label UI berbahasa Indonesia. Enum tidak mengambil nama tampilan sebagai kunci database.
- `Cache-Control: no-store` untuk auth dan respons pribadi di intermediary HTTP. Cache aplikasi yang sengaja dibuat harus terisolasi per user.

## 2. Data model

### 2.1 Entitas

| Entitas | Field dan constraint |
|---|---|
| users | id UUID PK; name varchar(60); normalized_email varchar(254) UNIQUE; password_hash; timezone IANA; created_at, updated_at |
| sessions | id UUID PK; user_id FK; refresh_token_hash UNIQUE; expires_at; revoked_at nullable; created_at |
| waste_logs | id UUID PK; user_id FK; category enum; method enum; quantity_kind enum; weight_g nullable; item_count nullable; activity_date date; activity_timezone; notes varchar(500); photo_id nullable; client_request_id UUID; request_hash; version int; created_at, updated_at; deleted_at nullable |
| waste_guides | id UUID PK; slug UNIQUE; name; category; keywords; recommended_method; summary; steps JSON; cautions JSON; source_url; reviewed_at; publication_status |
| achievements | id UUID PK; code UNIQUE; name; description; requirement_type; requirement_value integer |
| user_achievements | user_id FK + achievement_id FK composite PK; unlocked_at; derived dari catatan aktif |
| photos (opsional) | id UUID PK; user_id FK; object_key UNIQUE; mime_type; size_bytes; status; created_at |

`xp`, `level`, dan `streak` pada entitas User di PDF menjadi field respons turunan pada rancangan ini. Untuk dataset MVP, hitung dari catatan aktif agar edit/hapus tidak membuat cache progres salah. Jangan menyimpan versi kedua tanpa strategi invalidasi. `user_achievements` hanya perlu disimpan jika fitur achievement diaktifkan dan timestamp unlock dibutuhkan.

### 2.2 Enum

```text
category: ORGANIC | PLASTIC | PAPER | GLASS | METAL | ELECTRONIC | OTHER
method: REDUCE | REUSE | RECYCLE | TRASH
quantity_kind: WEIGHT | COUNT
publication_status: DRAFT | PUBLISHED | ARCHIVED
requirement_type: ACTIVE_LOG_COUNT | RECYCLE_LOG_COUNT | BEST_STREAK
```

### 2.3 Constraint database

Contoh SQL berikut menunjukkan constraint yang harus ada; bukan migrasi produksi lengkap.

```sql
CHECK (
  (quantity_kind = 'WEIGHT'
    AND weight_g IS NOT NULL AND weight_g BETWEEN 1 AND 100000
    AND item_count IS NULL)
  OR
  (quantity_kind = 'COUNT'
    AND item_count IS NOT NULL AND item_count BETWEEN 1 AND 10000
    AND weight_g IS NULL)
);
UNIQUE (user_id, client_request_id);
CHECK (version >= 1);
```

Gunakan NOT NULL untuk user_id, category, method, quantity_kind, activity_date, activity_timezone, client_request_id, request_hash, version, created_at, updated_at. Validasi enum di DB. Index aktif untuk `(user_id, activity_date DESC, id DESC) WHERE deleted_at IS NULL`, index filter kategori/metode ditambahkan bila query plan menunjukkan kebutuhan. Soft-delete mempertahankan identitas request agar replay create setelah delete tidak membuat aktivitas baru; hard-delete/retensi diputuskan terpisah.

Seluruh query objek pribadi mencantumkan `user_id = authenticated_user`. UUID yang sulit ditebak bukan kontrol akses. Object storage tidak boleh memakai public bucket.

### 2.4 Relasi

```mermaid
erDiagram
    USER ||--o{ SESSION : memiliki
    USER ||--o{ WASTE_LOG : mencatat
    USER ||--o{ USER_ACHIEVEMENT : memperoleh
    ACHIEVEMENT ||--o{ USER_ACHIEVEMENT : mendefinisikan
    USER ||--o{ PHOTO : mengunggah
    WASTE_LOG o|--o| PHOTO : melampirkan
```

Satu photo_id maksimal terkait satu log aktif dan harus milik user yang sama. WasteGuide tidak memiliki relasi kepemilikan pribadi; hanya record PUBLISHED yang dapat dibaca pengguna.

## 3. Daftar endpoint

| Method | Endpoint | Tujuan | Status |
|---|---|---|---|
| POST | /v1/auth/register | Buat akun + sesi | Dari PDF, diperinci |
| POST | /v1/auth/login | Buat sesi | Dari PDF |
| POST | /v1/auth/refresh | Rotasi refresh token | Tambahan rancangan |
| POST | /v1/auth/logout | Cabut sesi sekarang | Tambahan rancangan |
| GET | /v1/me | Profil dan progres | Dari PDF |
| GET | /v1/dashboard | Ringkasan hari ini dan 3 aktivitas terbaru | Tambahan rancangan |
| GET | /v1/metadata | Kategori, metode, batas validasi, capability | Tambahan rancangan |
| GET | /v1/waste-logs | Riwayat, filter, pagination | Dari PDF |
| POST | /v1/waste-logs | Tambah catatan | Dari PDF |
| GET | /v1/waste-logs/{id} | Detail milik sendiri | Dari PDF |
| PATCH | /v1/waste-logs/{id} | Koreksi catatan | Memperjelas CRUD PDF |
| DELETE | /v1/waste-logs/{id} | Hapus catatan | Memperjelas CRUD PDF |
| GET | /v1/waste-guide | Daftar/pencarian | Dari PDF |
| GET | /v1/waste-guide/{id} | Detail artikel | Tambahan rancangan |
| GET | /v1/statistics | Ringkasan, seri harian, kategori, metode | Dari PDF |
| GET | /v1/achievements | Achievement dan progres | Dari PDF, fitur opsional |
| POST | /v1/photos | Upload multipart gambar privat | Opsional |
| DELETE | /v1/photos/{id} | Hapus lampiran belum terpakai | Opsional |
| POST | /v1/ai/detections | Scan foto sementara | AI extension §10 |
| GET | /v1/ai/objects | Katalog koreksi objek | AI extension §10 |
| POST | /v1/ai/recommendations | Rekomendasi tervalidasi | AI extension §10 |
| DELETE | /v1/ai/detections/{id} | Buang sesi scan | AI extension §10 |
| GET | /health/live | Proses hidup | Operasional |
| GET | /health/ready | Aplikasi dan DB siap | Operasional, jangan bocorkan detail |

Tidak ada endpoint Maps dalam baseline. Saat opsi peta dipilih, tentukan provider/list lokasi terverifikasi dan kontraknya tersendiri. Tidak ada alamat dummy yang disajikan sebagai data fasilitas sungguhan.

## 4. Autentikasi dan profil

### Register

```http
POST /v1/auth/register
Content-Type: application/json
```

```json
{
  "name": "Pengguna Demo",
  "email": "demo@example.com",
  "password": "contoh-kata-sandi-panjang",
  "timezone": "Asia/Jakarta"
}
```

201 menghasilkan `user`, `access_token`, `access_expires_at`, `refresh_token`, `refresh_expires_at`, dan `session_id` di dalam `data`. Login memakai email/password dan menghasilkan payload sesi yang sama dengan 200. Contoh credential ini untuk format dokumentasi, jangan membuat akun seed dengan password tersebut.

Default usulan: access token 15 menit dan refresh session 7 hari. Nilai dipin di konfigurasi backend, bukan fakta dari PDF. JWT berisi `sub`, `sid`, `iat`, `exp`, issuer, audience. Verifikasi algoritma yang diizinkan, tanda tangan, issuer/audience, expiry, dan sesi yang belum dicabut. Refresh token opaque acak kriptografis; hanya hash token disimpan server.

Refresh menerima refresh_token, mencabut token lama, dan menerbitkan pasangan baru secara atomik. Aplikasi menserialisasi refresh agar beberapa request 401 tidak memicu rotasi bersamaan. Kegagalan rotasi yang hasilnya tidak diketahui meminta login ulang; jangan mengulangi refresh tanpa batas. Logout terautentikasi mencabut session_id yang ada pada JWT, menghasilkan 204 dan idempoten. Setelah logout gagal jaringan, client tetap menghapus rahasia lokal serta menyatakan sesi perangkat telah dihapus; sesi server belum boleh diklaim dicabut. Batas expiry tetap berlaku.

### GET /me

```json
{
  "data": {
    "id": "00000000-0000-4000-8000-000000000001",
    "name": "Pengguna Demo",
    "email": "demo@example.com",
    "timezone": "Asia/Jakarta",
    "progress": {
      "active_log_count": 5,
      "xp": 50,
      "level": 1,
      "xp_into_level": 50,
      "xp_to_next_level": 50,
      "current_streak": 2,
      "best_streak": 2,
      "has_logged_today": true
    }
  }
}
```

Password hash tidak pernah dikirim. Bila streak belum diaktifkan, field streak boleh null dan UI menyembunyikan bagian tersebut, bukan menunjukkan nol seolah sudah dihitung. Metadata mengirim capability boolean seperti `achievements`, `reminders`, `photos`, `recycling_points` untuk menyelaraskan opsi yang sudah diimplementasikan; jangan menjadikan flag sebagai pengganti authorization.

## 5. Catatan aktivitas

### Create

```json
{
  "client_request_id": "00000000-0000-4000-8000-000000000011",
  "category": "PLASTIC",
  "method": "RECYCLE",
  "quantity_kind": "WEIGHT",
  "weight_g": 250,
  "item_count": null,
  "activity_date": "2026-09-21",
  "notes": "Contoh data simulasi.",
  "photo_id": null
}
```

Server menetapkan timezone dari akun. 201 mengirim seluruh WasteLog termasuk id, activity_timezone, version=1, timestamp server, dan `progress` terbaru. Untuk COUNT kirim weight_g=null dan item_count integer. Field berlebih seperti user_id/xp/created_at ditolak, bukan dipakai.

### Idempotensi dan timeout

Android membuat `client_request_id` satu kali ketika draf baru disiapkan, menyimpannya bersama draf nonrahasia. Tombol simpan disabled ketika request aktif. Server melakukan insert dan pemeriksaan uniqueness dalam transaksi.

| Keadaan | Hasil |
|---|---|
| ID belum pernah diterima user ini | Simpan satu log, 201 |
| ID sama + payload kanonik sama | Kembalikan log yang sama, 200; tidak menambah XP |
| ID sama + payload berbeda | 409 IDEMPOTENCY_CONFLICT; jangan menimpa |
| ID sama tetapi log sudah dihapus | 410 LOG_DELETED; jangan menghidupkan ulang |
| Server commit tetapi respons hilang | Retry payload yang sama dengan ID yang sama |

Client tidak boleh mengedit lalu mengirim payload baru memakai ID request yang hasilnya belum diketahui. Resolve dulu dengan retry payload awal; setelah objek ditemukan, buka edit. Jika pengguna ingin aktivitas lain, mulai draf baru dengan ID baru. Uniqueness `(user_id, client_request_id)` tetap terjaga terhadap request paralel.

### List/filter

```http
GET /v1/waste-logs?category=PLASTIC&method=RECYCLE&from=2026-09-15&to=2026-09-21&limit=20
```

Kategori dan metode masing-masing satu nilai atau tidak disertakan. Filter AND, tanggal inklusif. `limit` default 20, maksimum 100. Cursor opaque memuat posisi `(activity_date,id)` dan terikat pada filter serta user; cursor bukan offset. Urutan `activity_date DESC, id DESC`. Saat filter berubah, reset cursor dan daftar. Pull-to-refresh memulai halaman pertama; client deduplikasi berdasarkan id saat pagination karena data dapat berubah.

```json
{
  "data": [],
  "page": {"next_cursor": null, "has_more": false}
}
```

Daftar kosong adalah 200. Kegagalan halaman berikutnya mempertahankan halaman sebelumnya dengan retry di bagian bawah.

### Read/update/delete

GET id sukses 200; objek hilang, dihapus, atau milik akun lain sama-sama 404. PATCH menerima field mutable dan `version` saat dibaca. Field yang tidak disertakan tidak berubah; `notes: null` berarti kosong; immutable field ditolak. Setelah penggabungan, lakukan validasi XOR weight/count yang sama seperti create.

activity_date lama yang tidak berubah boleh dipertahankan di luar jendela create. Tanggal baru pada PATCH harus berada antara hari ini dan 30 hari sebelumnya dalam timezone akun. Nilai notes null dinormalisasi ke string kosong sebelum disimpan.

```json
{"version": 1, "weight_g": 300, "notes": "Berat diperbaiki."}
```

Update memakai conditional query `WHERE id=? AND user_id=? AND version=? AND deleted_at IS NULL`, menaikkan version. 409 VERSION_CONFLICT mengharuskan UI memuat versi terbaru sebelum menerapkan ulang perubahan, dengan draf pengguna tetap tersedia. Jangan auto-merge berat/metode.

DELETE memakai header `If-Match: "<version>"`; missing header → 428 PRECONDITION_REQUIRED. Ketidakcocokan → 409 VERSION_CONFLICT. Delete berhasil → 204, `deleted_at` terisi, version bertambah. Replay delete atas objek milik sendiri yang sudah dihapus → 204; objek akun lain → 404. Semua agregat mengecualikan deleted_at.

## 6. Dashboard dan statistik

GET dashboard mengambil tanggal hari ini menurut timezone akun, bukan parameter client. Respons meliputi `date`, `timezone`, `summary`, `recent_logs` maksimal 3, dan `progress` sepanjang waktu. Summary memakai kontrak agregat yang sama dengan statistics.

```http
GET /v1/statistics?from=2026-09-15&to=2026-09-21
```

```json
{
  "data": {
    "from": "2026-09-15",
    "to": "2026-09-21",
    "timezone": "Asia/Jakarta",
    "summary": {
      "total_handled_weight_g": 1400,
      "total_handled_count": 0,
      "avoided_weight_g": 0,
      "avoided_count": 2,
      "activity_count": 5,
      "handled_activity_count": 4,
      "recycled_activity_count": 2,
      "recycling_rate_percent": 50.0,
      "recycling_rate_basis": "HANDLED_ACTIVITY_COUNT"
    },
    "method_counts": {"REDUCE": 1, "REUSE": 1, "RECYCLE": 2, "TRASH": 1},
    "daily": [
      {"date": "2026-09-15", "handled_weight_g": 0, "handled_count": 0, "activity_count": 0},
      {"date": "2026-09-16", "handled_weight_g": 0, "handled_count": 0, "activity_count": 0},
      {"date": "2026-09-17", "handled_weight_g": 0, "handled_count": 0, "activity_count": 0},
      {"date": "2026-09-18", "handled_weight_g": 0, "handled_count": 0, "activity_count": 0},
      {"date": "2026-09-19", "handled_weight_g": 0, "handled_count": 0, "activity_count": 0},
      {"date": "2026-09-20", "handled_weight_g": 750, "handled_count": 0, "activity_count": 2},
      {"date": "2026-09-21", "handled_weight_g": 650, "handled_count": 0, "activity_count": 3}
    ],
    "categories": [
      {"category": "ORGANIC", "handled_weight_g": 600, "handled_count": 0},
      {"category": "PLASTIC", "handled_weight_g": 250, "handled_count": 0},
      {"category": "PAPER", "handled_weight_g": 150, "handled_count": 0},
      {"category": "GLASS", "handled_weight_g": 400, "handled_count": 0},
      {"category": "METAL", "handled_weight_g": 0, "handled_count": 0},
      {"category": "ELECTRONIC", "handled_weight_g": 0, "handled_count": 0},
      {"category": "OTHER", "handled_weight_g": 0, "handled_count": 0}
    ]
  }
}
```

Hari tanpa aktivitas tetap dikirim nol. Data kosong mengirim array kategori dan hari dengan nol, recycling_rate_percent=null. Filter tanggal invalid atau >366 hari → 422. API tidak mengirim CO2 saved, nilai uang, atau berat hasil perkiraan benda. UI memberi nama metrik dan basis; chart selalu memiliki ringkasan teks/tabel.

## 7. Panduan, achievement, dan foto

GET waste-guide menerima `q` maksimum 100 karakter, kategori opsional, limit/cursor. Query di-trim; string kosong berarti daftar. Cari nama dan keywords tanpa membedakan kapital. Backend memakai parameterized query, bukan merangkai SQL dari q.

Detail panduan mengirim id, name, category, recommended_method, summary, steps[], cautions[], source_url, reviewed_at. `reviewed_at` tidak boleh difabrikasi. Data PUBLISHED harus lolos pemeriksaan editorial; draft contoh di HTML tidak otomatis menjadi seed.

GET achievements mengirim id, title, description, current_value, target_value, is_unlocked, unlocked_at. Nilai progres dicap untuk tampilan, tetapi hitungan asli tidak dibuang. Semantik threshold dan koreksi mengikuti PRD §10.

Foto opsional diunggah dengan multipart langsung ke backend untuk MVP, bukan membuat protokol upload kompleks. Backend memeriksa byte signature, MIME, ukuran ≤5 MiB, dimensi/decompression limit, kepemilikan, menghapus metadata EXIF sensitif, lalu menyimpan ke bucket privat. Respons photo_id dan preview URL berumur pendek. Log hanya menerima photo_id, bukan URL sewenang-wenang. URL bertanda tangan diperoleh hanya setelah otorisasi; log tidak mencatat URL utuh.

Unggahan yang belum terhubung ke log dibersihkan setelah 24 jam (usulan retensi sementara). Jika penyimpanan log gagal, pengguna boleh mencoba kembali memakai photo_id yang sama selama masih valid. Foto yang terhubung ke log dihapus mengikuti kebijakan retensi log; akses pengguna langsung ditolak setelah log dihapus. Jangan menyatakan file telah terhapus permanen jika masih ada di backup.

## 8. Error contract

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Periksa kembali isianmu.",
    "fields": {"weight_g": "Berat minimal 1 gram."}
  },
  "request_id": "example-request-id"
}
```

| HTTP | Code | Perilaku Android |
|---|---|---|
| 400 | INVALID_REQUEST | Pesan terarah, jangan retry otomatis |
| 401 | UNAUTHENTICATED / SESSION_EXPIRED | Refresh sekali; login bila gagal |
| 404 | NOT_FOUND | Kembali ke daftar; tidak membocorkan pemilik lain |
| 409 | EMAIL_EXISTS | Sorot email registrasi |
| 409 | VERSION_CONFLICT | D04, pertahankan draf, tawarkan muat versi baru |
| 409 | IDEMPOTENCY_CONFLICT | Resolve catatan awal sebelum membuat draf baru |
| 410 | LOG_DELETED | Informasikan catatan replay telah dihapus |
| 413 | FILE_TOO_LARGE | Pilih foto lain atau lanjut tanpa foto |
| 415 | UNSUPPORTED_MEDIA_TYPE | Pilih format yang diizinkan |
| 422 | VALIDATION_ERROR | Error inline per field, fokus error pertama |
| 428 | PRECONDITION_REQUIRED | Ambil versi objek sebelum hapus |
| 429 | RATE_LIMITED | Hormati Retry-After; jangan retry loop |
| 500/503 | INTERNAL_ERROR / UNAVAILABLE | Pesan generik + retry; request_id untuk diagnosis |

Tidak ada stack trace, SQL, token, email pemilik lain, atau path internal pada respons error. Network timeout bukan bukti transaksi gagal; create wajib menggunakan mekanisme idempotensi.

## 9. Transaksi, keamanan, dan kontrak pengujian

Create, uniqueness request, dan perubahan derived achievement dilakukan dalam transaksi yang menjaga satu hasil domain. Untuk MVP XP/statistik dihitung langsung dari log aktif. Endpoint summary menggunakan snapshot transaksi konsisten jika menggabungkan beberapa query. Validasi server tetap wajib meski client sudah memvalidasi.

Password disimpan dengan Argon2id melalui library yang terpelihara; simpan parameter dan salt bersama hash, jangan membuat fungsi hashing sendiri. Pilihan ini mengacu pada [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html). Rate limit auth per IP dan identitas ternormalisasi, tanpa mengunci permanen pengguna lewat serangan pihak lain; nilai limit disepakati setelah pengujian.

Kasus wajib: user A tidak dapat membaca/mengubah/menghapus log user B; integer batas bawah/atas; XOR unit; semua enum invalid; hanya Reduce; tidak ada catatan; campuran unit; request create paralel dengan ID sama; version conflict; replay setelah delete; token expired/revoked; tanggal sekitar pergantian hari. Test penerimaan bersama Android/backend menggunakan dataset PRD §9.4.

## 10. AI extension v1.2

Pembaruan integrasi 20 Oktober 2026: [kontrak HTTP v1](../../../ai/docs/AI_HTTP_CONTRACT.md) dan [OpenAPI publik](../../../ai/contracts/v1/public.openapi.json) menjadi acuan mesin yang lebih lengkap dari contoh ringkas di bawah. Envelope menambah api_version=1.0.0; request rekomendasi menambah confirmed_object=true, input_source dan context_revision. Python adalah pemilik tunggal mapping/policy/Gemini; Go membungkus hasil dengan sesi/ownership/TTL/dedup dan penyimpanan. Mock publik tersedia; backend Go produksi tetap pekerjaan lanjutan.

Kontrak ini memperluas core, menggunakan prefix `/v1` yang sama (contoh `/api/v1` pada plan dinormalisasi). Semua payload di bawah contoh rancangan. [AI_SPEC](AI_SPEC.md) menetapkan mapping, kebijakan rekomendasi, batas media, TTL dan retensi. Tidak ada endpoint AI yang membuat log atau XP.

| Method | Endpoint | Fungsi |
|---|---|---|
| GET | /v1/ai/objects | Katalog object_key/category yang didukung dan versi mapping |
| POST | /v1/ai/detections | Upload foto ephemeral, jalankan YOLO, simpan sesi hasil user-scoped |
| POST | /v1/ai/recommendations | Konfirmasi/koreksi objek, retrieval panduan, saran tervalidasi |
| DELETE | /v1/ai/detections/{id} | Buang sesi sendiri dan rekomendasi sementara; idempotent 204 |

GET /v1/ai/objects mengembalikan data.items berisi object_key, label, category, special_handling dan catalog_version; tidak memuat arbitrary prompt. Kategori OTHER dapat dipilih manual tanpa memaksa object_key palsu.

GET /v1/metadata menambah `capabilities.ai_scan` dan `capabilities.ai_recommendation` boolean serta `ai_object_catalog_version` nullable. Capability false tetap mempertahankan tombol Catat dengan input manual; bukan menyembunyikan seluruh pencatatan. Konfigurasi model mentah dan secret tidak masuk metadata publik.

### 10.1 Deteksi

Multipart fields: `image` JPEG/PNG, `client_request_id` UUID. Tidak menerima URL gambar eksternal/base64 JSON. File ≤5 MiB, decoded ≤12 MP dan sisi ≤4096, normalisasi orientasi/EXIF server-side. Autentikasi dan pembatasan akun dilakukan sebelum inference. Response 201 pertama, 200 replay identik selama TTL; ID sama dengan digest gambar berbeda → 409 IDEMPOTENCY_CONFLICT. Dedup scoped user dan digest tidak menjadi data training.

```json
{
  "data": {
    "detection_id": "7d58dd23-03c8-4f68-8caa-cefb0a720100",
    "status": "REVIEW_REQUIRED",
    "model_version": "candidate-unvalidated-01",
    "mapping_version": "mapping-01",
    "expires_at": "2026-09-29T03:00:00Z",
    "image": {"width": 960, "height": 1280},
    "objects": [{
      "object_id": "object-1",
      "raw_class": "bottle",
      "confidence": 0.94,
      "bbox": [0.12, 0.08, 0.62, 0.88],
      "confidence_band": "OFFERED",
      "object_key": null,
      "category": null,
      "requires_material_confirmation": true
    }]
  },
  "request_id": "request-example-1"
}
```

Tidak ada foto/URL permanen pada respons. `status`: REVIEW_REQUIRED bila ada kandidat ≥ambang lower; MANUAL_REQUIRED bila kosong/unknown/semua rendah. `objects` dapat kosong. Semua score finite 0..1; bbox tervalidasi sesuai AI_SPEC. `object_id` hanya unik di detection session. Raw class dan category berbeda. Skor rendah memakai UNCERTAIN; hasil <0,50 tidak dijadikan prefill. Nilai threshold adalah konfigurasi versioned, bukan magic constant UI.

### 10.2 Rekomendasi

```json
{
  "client_request_id": "5131c085-e592-4fe8-bd69-cf6b56d33be6",
  "detection_id": "7d58dd23-03c8-4f68-8caa-cefb0a720100",
  "object_id": "object-1",
  "confirmed_object_key": "pet_bottle",
  "confirmed_category": "PLASTIC",
  "condition": {"cleanliness": "CLEAN", "integrity": "INTACT", "reusable": "UNKNOWN", "cleanable": "UNKNOWN", "soiling": "UNKNOWN"},
  "reuse_intention": false,
  "risk_flag": false,
  "risk_type": "UNKNOWN",
  "recommendation_goal": "AUTO",
  "recycling_access": "UNKNOWN"
}
```

Server mengambil confidence/model/mapping dari sesi sendiri, bukan percaya nilai kiriman client. `confirmed_object_key` berasal dari katalog server; kategori harus cocok atau 422. Untuk kelas unknown tanpa panduan, gunakan manual dan jangan membuat recommendation palsu. Tidak menerima user_id, arbitrary guide_key, URL/prompt bebas atau allowed_methods dari client. Ownership dicek sebelum lookup model/LLM.

```json
{
  "data": {
    "recommendation_id": "10f6a0b0-533d-4fd4-85a7-ecf38e42b101",
    "status": "READY",
    "category": "PLASTIC",
    "recommended_method": "RECYCLE",
    "recommendation_scope": "HANDLING",
    "reason": "Contoh respons: periksa ketentuan penerimaan dalam panduan fasilitas.",
    "special_handling": false,
    "caution": "Ini contoh kontrak, bukan rekomendasi yang sudah diverifikasi.",
    "guide_refs": [{"guide_id": "9fbd97ab-9450-4b6e-a43e-e0bda8a61011", "version": 1}],
    "source_urls": ["https://www.epa.gov/recycle/how-do-i-recycle-common-recyclables"],
    "steps": ["Periksa ketentuan fasilitas penerima."],
    "recommendation_version": "pilot-policy-05",
    "expires_at": "2026-09-29T03:00:00Z"
  },
  "request_id": "request-example-2"
}
```

`status` READY / NEEDS_CONTEXT / UNAVAILABLE. READY memiliki tepat satu recommended_method dari REDUCE/REUSE/RECYCLE/TRASH, reason, source_urls dan recommendation_scope=PREVENTION untuk REDUCE atau HANDLING untuk lainnya. source_details menambahkan judul/penerbit/wilayah/checked_at/scope_note dari registri. Tidak ada alternative_method atau status SPECIAL_HANDLING. special_handling=true adalah peringatan tambahan yang boleh menyertai READY. Contoh: kaca pecah READY/TRASH/true, baterai READY/RECYCLE/true melalui pengelola khusus. recommendation_goal=AUTO/PREVENTION/HANDLING dan recycling_access=YES/NO/UNKNOWN memiliki default AUTO/UNKNOWN; REDUCE hanya ditawarkan untuk PREVENTION yang dipilih pengguna.

Tambahan policy-04: condition.cleanable=YES/NO/UNKNOWN, condition.soiling=FOOD_DRINK/DUST/HAZARDOUS/UNKNOWN, risk_type=UNKNOWN/SHARP/CHEMICAL/BIOLOGICAL/ELECTRICAL/OTHER; default UNKNOWN. Peralatan kotor biasa yang dikonfirmasi bisa dibersihkan dapat REUSE setelah dicuci. NEEDS_CONTEXT memakai metode/scope null dan missing_context (misalnya ["risk_type"] atau ["condition.cleanable"]), tanpa memanggil Gemini. UNAVAILABLE memakai metode/scope null ketika panduan/layanan/output tidak siap atau risiko tidak tercakup; caution risiko dipertahankan. Tidak mengembalikan exception/provider raw text. Expiry recommendation tidak boleh melampaui sesi deteksi. Konfirmasi pengguna tidak disimpulkan dari tombol generate.

Simpan rekomendasi identik/replay satu client_request_id tanpa memanggil provider lagi selama TTL. Payload berbeda dengan ID sama →409. Context berubah memakai request ID baru; respons lama dibuang di Android. Infrastruktur timeout memakai error 504 AI_TIMEOUT; provider unavailable 503 AI_UNAVAILABLE; output semantik invalid dapat kembali 200 status UNAVAILABLE sebagai hasil fallback yang terkontrol.

### 10.3 Penyimpanan catatan dan metadata

POST /v1/waste-logs tetap satu-satunya create path. Tambahan opsional pada body existing:

```json
{
  "ai_provenance": {
    "recommendation_id": "10f6a0b0-533d-4fd4-85a7-ecf38e42b101",
    "user_confirmed_object": true,
    "user_confirmed_method": true
  }
}
```

Ini fragmen tambahan, bukan request create lengkap; category/method/quantity/date/client_request_id tetap wajib seperti §5. Manual menghilangkan ai_provenance. Server memvalidasi ownership, TTL, kategori sama dengan konfirmasi, dan kedua confirmation=true. Metode akhir boleh berbeda dari saran; simpan final_category/final_method dari waste log dan flag accepted berdasarkan perbandingan server, bukan boolean client sebagai ukuran akurasi. REDUCE hanya lewat manual tanpa provenance rekomendasi disposal.

Satu recommendation_id boleh terikat satu waste_log_id; constraint unik scoped user untuk mencegah double-save dengan request ID baru. Replay ID create core identik mengembalikan record yang sama. Reuse recommendation oleh log berbeda →409 AI_ALREADY_LINKED. Provenance invalid/expired →422 AI_REFERENCE_INVALID / 410 AI_SESSION_EXPIRED; pertahankan draf dan tawarkan simpan manual setelah pengguna melepas provenance. Jangan diam-diam mengubah hasil request idempotent.

Entitas tambahan:

| Entitas | Field minimum |
|---|---|
| ai_detection_sessions | id, user_id, client_request_id, image_digest, model_version, mapping_version, detections JSON validated, created_at, expires_at; UNIQUE(user_id,client_request_id) |
| ai_recommendations | id, detection_id, user_id, client_request_id, request_hash, confirmed_object_key/category, condition JSON, reuse_intention, status, methods nullable, guide_snapshot_refs, policy/provider/model version, created_at, expires_at |
| ai_log_metadata | waste_log_id UNIQUE FK, recommendation_id UNIQUE, user_id, model/mapping/policy/guide versions, raw_class/confidence, confirmed object/category, original suggestion, final category/method at creation, confirmation flags; tidak menyimpan foto/prompt mentah |

Saat create, snapshot metadata dan waste log dibuat dalam transaksi yang sama. Snapshot tetap cukup untuk audit setelah sesi ephemeral dihapus (reference ID di metadata tidak menjadi FK yang memaksa sesi tetap hidup). Edit log tidak menghitung ulang model atau mengubah snapshot awal. Delete mengikuti retensi core dan menghapus metadata pada purge; statistik tidak membaca tabel AI. Migration tambahan nullable/terpisah sehingga log manual lama tetap valid.

### 10.4 Error dan pengujian kontrak

413/415/422 untuk ukuran/tipe/decoder; 401 sesi; 404 session milik orang lain; 410 AI_SESSION_EXPIRED; 429 RATE_LIMITED + Retry-After; 503 AI_UNAVAILABLE/AI_DISABLED; 504 AI_TIMEOUT. Error envelope tetap §8. Retry upload/inference harus eksplisit dan dibatasi; retry simpan core mempertahankan ID yang sama.

Uji malformed bbox/NaN, spoof category/guide/confidence, AI lintas akun, replay identik/berbeda, expiry, special handling override, LLM schema/evidence invalid, unknown class, no guide, timeout, cancel, log transaction rollback, concurrent save, dan log manual tanpa metadata. Jangan mencatat body gambar/prompt/Authorization di access log.

### Pembaruan kualitas pilot, 20 Oktober 2026

Kontrak Python v1 menambahkan review_profile dan review_thresholds/review_reasons/ambiguous_with per kandidat, serta condition.former_contents=FOOD_DRINK/CHEMICAL/OTHER/UNKNOWN. Band server mengutamakan profil checkpoint; tabel threshold global adalah default COCO. Kandidat empat kelas SHA af1446f33338... memakai dw-val-01: can/HDPE OFFERED >=0,70; PET >=0,725; kaca selalu UNCERTAIN; beda kelas dengan overlap IoU>=0,65 tetap terlihat dan UNCERTAIN. Semua kandidat memerlukan konfirmasi pengguna.

Go mengirim model_sha256 dari snapshot privat pada selection agar aturan bisa diperiksa ulang; tidak menerima provenance tersebut dari frontend. Pilot RECYCLE memerlukan recycling_access=YES; HDPE memerlukan former_contents=FOOD_DRINK. UNKNOWN meminta konteks; tidak ditebak menjadi Trash/Reduce. Semua 69 panduan masih DRAFT. Rincian, metrik val beserta tradeoff, evaluasi Gemini dan perintah reproduksi: [mutu deteksi](../../../ai/docs/DETECTION_QUALITY.md), [mutu rekomendasi](../../../ai/docs/RECOMMENDATION_EVALUATION.md). OpenAPI pada ai/contracts/v1 merupakan acuan DTO yang diregenerasi.

### Saran bersyarat saat kondisi kosong, policy-06

Konteks biasa yang belum diisi tetap UNKNOWN/null. allow_conditional default true mengizinkan READY dengan recommendation_basis=CONDITIONAL dan conditions_to_verify; false mempertahankan mode ketat NEEDS_CONTEXT. UI menampilkan saran sementara dan syarat, tanpa mengisi data kosong atau menyimpan otomatis. Risiko ambigu, kontaminasi berbahaya dan riwayat isi HDPE tetap dibatasi. Lihat [perilaku terbaru, reproduksi dan hasil pengujian](../../../ai/docs/CONDITIONAL_AND_BACKGROUND.md).
