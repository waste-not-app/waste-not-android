# Titip.in — Backend Improvement Docs
*Dokumen ini dibuat untuk melanjutkan development BE di chat terpisah*
*Prioritas diurutkan dari yang paling urgent untuk FE bisa jalan penuh*

---

## 🔴 PRIORITAS TINGGI — FE Blocked

### 1. Filter by `userId` di Jastip & Preloved

**Problem:** FE sudah punya screen "Jastip Saya" dan "Preloved Saya" yang fetch data milik user yang login. Saat ini `GET /jastip` dan `GET /preloved` return semua data, tidak bisa filter per user.

**Yang dibutuhkan:** Support query param `userId` di kedua endpoint.

```
GET /jastip?userId={userId}     → return hanya jastip milik userId tsb
GET /preloved?userId={userId}   → return hanya preloved milik userId tsb
```

**Catatan implementasi:**
- Kalau `userId` tidak disertakan → behavior seperti sekarang (return semua ACTIVE/AVAILABLE)
- Kalau `userId` disertakan → return semua status milik user (ACTIVE + CLOSED untuk jastip, AVAILABLE + SOLD + RESERVED untuk preloved)
- Endpoint ini butuh auth (`🔐`) karena hanya owner yang boleh lihat semua status miliknya

---

### 2. Stats di `/auth/me`

**Problem:** Screen Profil menampilkan "—" untuk Jastip, Preloved, dan Review karena response `/auth/me` tidak include stats.

**Yang dibutuhkan:** Tambah field stats di response `UserDto`:

```json
// Response GET /auth/me — tambah field berikut:
{
  "id": "...",
  "name": "...",
  "email": "...",
  "waNumber": "...",
  "avatarUrl": null,
  "createdAt": "...",

  // TAMBAH INI:
  "totalJastip": 12,      // count jastip milik user (semua status)
  "totalPreloved": 5,     // count preloved milik user (semua status)
  "totalReview": 48,      // count review yang diterima user
  "rating": 4.9           // average rating dari semua review
}
```

**Catatan:** `rating` bisa `null` kalau belum ada review. FE sudah handle null dengan tampil "—".

---

## 🟡 PRIORITAS SEDANG — Fitur Belum Complete

### 3. Error Message yang Human-Readable untuk 403

**Problem:** Saat user mencoba ambil request milik sendiri, BE return 403 tapi FE tidak tahu pesan yang tepat untuk ditampilkan ke user.

**Yang dibutuhkan:** Pastikan semua response 403 include `error.message` yang jelas:

```json
// Contoh: user coba take request milik sendiri
{
  "success": false,
  "error": {
    "code": "FORBIDDEN",
    "message": "Kamu tidak bisa mengambil request milik sendiri"
  }
}

// Contoh: user coba fulfill wanted milik sendiri
{
  "success": false,
  "error": {
    "code": "FORBIDDEN",
    "message": "Kamu tidak bisa fulfill wanted milik sendiri"
  }
}
```

FE sudah punya Snackbar yang langsung tampilkan `error.message` — tinggal pastikan BE kirim pesan yang proper.

---

### 4. Endpoint DELETE untuk Request & Wanted

**Problem:** API docs tidak punya endpoint hapus untuk Request dan Wanted. FE butuh ini untuk fitur "Jastip Saya" / manajemen milik sendiri di masa depan.

**Yang dibutuhkan:**

```
DELETE /requests/{id}   🔐  → hapus request (owner only)
DELETE /wanted/{id}     🔐  → hapus wanted (owner only)
```

Response pattern sama seperti DELETE jastip/preloved:
```json
{ "message": "Request berhasil dihapus" }
```

Error cases: `403` kalau bukan owner, `404` kalau tidak ditemukan.

---

### 5. Fix Trailing Space pada Field `name`

**Problem:** BE return `"name": "Oktavianus "` (ada trailing whitespace). FE sudah workaround dengan `.trim()` di semua tempat, tapi lebih bersih kalau di-fix di layer BE.

**Fix:** Trim `name` saat save ke database, atau trim saat return di response.

---

## 🔵 PRIORITAS RENDAH — Post-MVP / Fitur Besar

### 6. Rating & Review System

**Problem:** Belum ada sama sekali di BE. Screen Review & Rating di FE sudah dibuat tapi masih empty state karena tidak ada data.

**Yang dibutuhkan:**

#### Tabel baru: `reviews`
```sql
CREATE TABLE reviews (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  reviewer_id UUID REFERENCES users(id),  -- yang memberi review
  target_id   UUID REFERENCES users(id),  -- yang menerima review
  rating      INTEGER CHECK (rating BETWEEN 1 AND 5),
  comment     TEXT,
  source_type VARCHAR(20),  -- "JASTIP" atau "PRELOVED"
  source_id   UUID,         -- id jastip/preloved yang jadi dasar transaksi
  created_at  TIMESTAMP DEFAULT NOW()
);
```

#### Endpoints baru:
```
POST /reviews              🔐  → kirim review setelah transaksi
GET  /users/{id}/reviews   🔐  → list review yang diterima user
```

**Request body POST /reviews:**
```json
{
  "targetId":   "uuid-user-yang-direview",
  "rating":     5,
  "comment":    "Jastipnya cepet dan aman!",
  "sourceType": "JASTIP",
  "sourceId":   "uuid-jastip"
}
```

**Response GET /users/{id}/reviews:**
```json
[
  {
    "id": "...",
    "reviewer": { "name": "...", "avatarUrl": null },
    "rating": 5,
    "comment": "...",
    "sourceType": "JASTIP",
    "createdAt": "..."
  }
]
```

**Business rules:**
- User hanya bisa review setelah transaksi selesai (status CLOSED)
- Satu transaksi hanya bisa di-review sekali
- User tidak bisa review diri sendiri

---

### 7. Redesign Flow Request & Wanted — Multi-Offer System

**Problem saat ini:** `PUT /requests/{id}/take` langsung set status TAKEN ke satu provider. Ini tidak ideal karena:
- Requester tidak bisa memilih provider yang paling cocok
- Tidak ada tahap konfirmasi dari requester
- Rating tidak bisa diimplementasikan dengan benar tanpa status CLOSED yang jelas

**Flow yang diinginkan:**

```
Request OPEN
  ↓ provider apply
Provider list (PENDING offers)
  ↓ requester pilih satu
Request TAKEN (provider terpilih)
  ↓ transaksi selesai
Request CLOSED
  ↓ bisa rating
Review tersimpan
```

#### Tabel baru: `request_offers`
```sql
CREATE TABLE request_offers (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  request_id  UUID REFERENCES requests(id),
  provider_id UUID REFERENCES users(id),
  status      VARCHAR(20) DEFAULT 'PENDING',  -- PENDING | ACCEPTED | REJECTED
  created_at  TIMESTAMP DEFAULT NOW()
);
```

#### Endpoint baru / modifikasi:

```
POST /requests/{id}/offer              🔐  → provider apply ke request
                                            (gantikan PUT /take)
GET  /requests/{id}/offers             🔐  → requester lihat list provider yang apply
PUT  /requests/{id}/offers/{offerId}/accept  🔐  → requester pilih provider
                                                    → status request jadi TAKEN
                                                    → status offer lain jadi REJECTED
PUT  /requests/{id}/close              🔐  → mark selesai (requester atau provider)
                                            → status jadi CLOSED
                                            → unlock fitur review
```

**Flow Wanted identik:**
```
POST /wanted/{id}/offer              🔐  → seller tawarkan barang
GET  /wanted/{id}/offers             🔐  → pencari lihat list yang offer
PUT  /wanted/{id}/offers/{offerId}/accept  🔐  → pencari pilih seller
PUT  /wanted/{id}/close              🔐  → mark selesai
```

**Catatan FE:** Untuk implementasi flow baru ini, FE perlu dibuat screen baru (list offers, konfirmasi) yang belum ada sekarang. Sebaiknya dikerjakan setelah BE endpoint selesai.

---

### 8. Update Profil (Edit Profil, WA, Password)

**Problem:** Screen Pengaturan di FE sudah ada tapi tombol Edit Profil, No. WA, dan Ubah Password masih placeholder karena BE belum punya endpoint update.

**Yang dibutuhkan:**

```
PUT /users/me          🔐  → update profil (nama, avatarUrl)
PUT /users/me/wa       🔐  → update nomor WhatsApp
PUT /users/me/password 🔐  → ubah password (butuh old password untuk verifikasi)
```

**Request body PUT /users/me:**
```json
{
  "name":      "Nama Baru",
  "avatarUrl": "https://..."   // optional
}
```

**Request body PUT /users/me/password:**
```json
{
  "oldPassword": "password_lama",
  "newPassword": "password_baru_min8"
}
```

---

## 📋 Summary Checklist untuk BE

| # | Fitur | Prioritas | Blocking FE? |
|---|-------|-----------|-------------|
| 1 | `GET /jastip?userId=` dan `GET /preloved?userId=` | 🔴 Tinggi | Ya — Jastip Saya & Preloved Saya |
| 2 | Stats di `/auth/me` (`totalJastip`, `totalPreloved`, `totalReview`, `rating`) | 🔴 Tinggi | Ya — Stats di Profil |
| 3 | Human-readable 403 error messages | 🟡 Sedang | Partial — Snackbar sudah ada |
| 4 | `DELETE /requests/{id}` dan `DELETE /wanted/{id}` | 🟡 Sedang | Tidak untuk MVP |
| 5 | Fix trailing space pada `name` | 🟡 Sedang | Tidak — FE sudah workaround |
| 6 | Rating & Review system | 🔵 Rendah | Ya — Review screen masih empty |
| 7 | Redesign Request/Wanted flow (multi-offer) | 🔵 Rendah | Tidak untuk MVP |
| 8 | Update profil endpoints | 🔵 Rendah | Tidak — FE sudah ada placeholder |

---

## 🔧 Catatan Teknis

**Timestamp format:** Pastikan semua field datetime (`createdAt`, `deadline`, dll) selalu return ISO 8601 lengkap: `"2026-03-20T14:00:00.000000"`. FE sudah handle dengan `.substringBefore(".")` tapi konsistensi lebih baik.

**Base URL FE:** `http://10.0.2.2:8080` (emulator) / `http://[IP_LOKAL]:8080` (device fisik)

**Auth:** Semua endpoint `🔐` butuh header `Authorization: Bearer <token>`. Token didapat dari `/auth/login` atau `/auth/register`, expire 24 jam.

**Global response format yang sudah dipakai FE:**
```json
// Success
{
  "success": true,
  "message": "OK",
  "data": <T>,
  "error": null
}

// Error
{
  "success": false,
  "message": null,
  "data": null,
  "error": {
    "code": "ERROR_CODE",
    "message": "Pesan error yang human-readable"
  }
}
```
