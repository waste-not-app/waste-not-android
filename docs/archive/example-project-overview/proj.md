---

## 📱 Project Summary: Jastip & Preloved Area Malang App

### 🎯 Latar Belakang / Problem

Di area Malang (khususnya mahasiswa), layanan jastip dan jual-beli preloved **tidak terpusat**. Saat ini tersebar di banyak grup WhatsApp/Telegram (contoh: UB Mager, Malang Jastip, dll) sehingga:

* Sulit mencari jastip yang sedang available
* Tidak bisa filter berdasarkan jarak terdekat
* Sulit membedakan mana user yang legit
* Preloved rawan penipuan karena tidak ada interface & trust layer

### 💡 Solusi

Membangun **aplikasi mobile (Android-first)** sebagai **platform perantara (bukan marketplace penuh)** untuk:

* Mencari & membuka jasa jastip di area Malang
* Membuat offer “mencari jastip” yang bisa diambil user lain
* Menawarkan & mencari barang preloved
* Semua transaksi dilakukan **di luar aplikasi (direct ke WhatsApp)**

Aplikasi hanya berperan sebagai:

> Centralized interface + discovery + trust layer ringan

---

## 👤 Use Case User

User bisa:

* Cari jastip
* Offer / buka jastip
* Cari barang preloved
* Offer / jual barang preloved
* Kontak langsung via WhatsApp

Tidak ada:

* In-app payment
* In-app checkout
* Escrow
* In-app chat (MVP)

---

## 🧭 Struktur Navigasi (FIX)

### Root Navigation: **Bottom Navigation**

```
Home | Jastip | Preloved | Profile
```

### Secondary Navigation: **Tab Layout**

* **Jastip**

  * Available
  * Cari Jastip
* **Preloved**

  * Dijual
  * Dicari

### Action Utama: **Context-aware FAB**

* Di Jastip → `+ Buka Jastip`
* Di Preloved → `+ Jual Barang`

❌ Drawer navigation tidak digunakan
❌ Tab layout tidak digunakan sebagai root

---

## 🔁 User Flow Utama

1. **Cari Jastip**

   * Home → List jastip → Detail → Chat WhatsApp
2. **Buka Jastip**

   * Jastip tab → FAB → Form → Submit
3. **Cari Preloved**

   * Preloved tab → Item → Chat WhatsApp
4. **Jual Preloved**

   * Preloved tab → FAB → Form → Submit

Prinsip:

* Maksimal 1–2 tap dari intent ke aksi
* Semua CTA jelas menuju WhatsApp

---

## 🖥️ Screen Utama (MVP)

* **Home**

  * Search jastip (from → to)
  * Filter jarak (3km / 5km / 10km)
  * List jastip terdekat
* **Jastip**

  * Available jastip
  * Offer “Cari jastip”
* **Preloved**

  * Dijual
  * Dicari
* **Profile**

  * Data user
  * Status verifikasi
  * Jastip & preloved aktif

---

## 📍 Fitur Teknis (MVP)

* GPS & lokasi user
* Maps (untuk jarak, bukan live tracking)
* Sort & filter berdasarkan jarak
* Status jastip: Open / Taken / Done
* Auto hide / expire offer
* Direct WhatsApp redirect
* Disclaimer: platform hanya perantara

---

## 🎨 UI/UX Strategy

### Pendekatan:

* Fokus **UI/UX dulu (Month 1)**
* Output: **Clickable prototype Figma**
* Validasi ke user (mahasiswa)

### Design Guidelines:

Tidak full design system, hanya **mini guidelines**:

* Color palette sederhana (3–5 warna)
* 1 font (Inter / Roboto)
* Komponen dasar:

  * Button
  * Card
  * Input
  * FAB
* Spacing konsisten (8 / 12 / 16)
* Status color (Open hijau, Taken abu)

Tujuan:

> Konsisten, jelas, mudah diimplementasi ke Android Material 3

---

## 🗓️ Roadmap Singkat

### Month 1

* UI/UX Design
* Navigation + user flow
* Wireframe → high-fidelity Figma
* Clickable prototype

### Month 2+

* Android development
* Backend (Firebase / Supabase)
* GPS & listing logic

---

## 🧠 Prinsip Utama Project

* Lawan utama: **grup WhatsApp**
* UX harus lebih cepat & lebih rapi dari WA
* Jangan overengineering
* MVP dulu, scalable belakangan

---

Oke bro, gue jawab **jujur, realistis, dan agak “dingin”** ya. Bukan buat ngejatuhin, tapi biar kamu **tau risiko & peluangnya dengan kepala dingin**.

Gue udah baca **petunjuk teknis Launchpad FILKOM** dan **template proposal** yang kamu kirim. Jadi ini bukan opini kosong.

---

## TL;DR dulu (biar jelas dari awal)

**Jawaban jujurnya:**

> ✅ **Masuk akal untuk diikutkan ke Launchpad FILKOM**
> ⚠️ **TAPI dengan catatan: posisikan sebagai “startup very-early stage / inkubasi produk”, BUKAN bisnis siap cuan**

Kalau ekspektasi kamu:

* “langsung rame”
* “cepet cuan”
* “langsung jadi startup besar”

❌ **itu nggak realistis dalam timeline & skema program ini**.

Tapi kalau tujuan kamu:

* lolos seleksi
* dapet inkubasi
* dapet pendanaan 10 jt
* PKL 4 SKS
* portfolio startup beneran

✅ **ini cocok.**

---

## Sekarang kita bedah satu per satu, sesuai kriteria Launchpad

---

## 1️⃣ Apakah IDE NYA RELEVAN dengan Launchpad FILKOM?

### Dari juknis:

> Startup berbasis **produk digital (software, utility, e-commerce, dll)**

📌 Project kamu:

* Platform digital (software)
* Location-based service
* Marketplace ringan (jastip & preloved)
* Fokus mahasiswa & komunitas lokal

✅ **MASUK KATEGORI**
Ini bukan ide “maksa masuk”, ini memang *software product*.

---

## 2️⃣ Apakah MASALAH yang kamu angkat cukup “kuat” secara akademik & bisnis?

### Problem kamu:

* Jastip terfragmentasi di banyak grup
* Tidak terstruktur
* Tidak bisa filter jarak
* Trust issue

Ini **masalah nyata**, tapi jujur ya:

⚠️ **Masalahnya BUKAN masalah “besar nasional”**
Ini masalah:

* lokal
* niche
* komunitas mahasiswa

Tapi buat Launchpad:

> ❗ **itu TIDAK masalah**

Karena program ini:

* inkubasi mahasiswa
* bukan cari unicorn
* lebih fokus ke *proses & produk*

📌 Selama kamu framing-nya:

> “Validasi di ekosistem kampus Malang sebagai pilot”

✅ itu justru **plus**, bukan minus.

---

## 3️⃣ Apakah IDE INI PUNYA NILAI EKONOMIS? (Ini krusial)

Gue jawab **jujur banget**:

### ❌ Monetisasi langsung = LEMAH (di awal)

* Kamu bukan pegang uang
* No payment
* No escrow
* No fee transaksi

Kalau dosen nanya:

> “Dapet uangnya dari mana?”

Kalau kamu jawab:

> “Nanti dulu”

❌ itu bahaya.

---

### Tapi… ada solusi framing yang REALISTIS

Model bisnis yang **masuk akal untuk proposal**:

* Freemium
* Sponsored listing jastip
* Featured preloved
* Partnership merchant lokal (kopi, laundry, travel)

👉 **Bukan sekarang dijalanin**, tapi:

> *punya rencana yang logis*

📌 Launchpad NILAI:

* apakah kamu mikir ke arah bisnis
* bukan apakah sudah profit

---

## 4️⃣ Apakah PROGRESS KAMU cukup buat LOLOS SELEKSI?

Dari juknis:

> *“Diutamakan sudah memiliki prototipe”*

📌 Posisi kamu sekarang:

* Ide matang
* User flow jelas
* Navigasi fix
* UI/UX sedang dikerjakan (Figma)

Kalau kamu:

* punya **clickable prototype**
* bisa jelasin flow dengan yakin

✅ **ITU SUDAH DIHITUNG PROTOTIPE**

Tidak harus:

* app jadi
* backend live

---



## Risiko REAL dari Project Ini (biar ga ilusi)

Gue rangkum risiko tanpa basa-basi:

### Risiko teknis

* App bisa jadi sepi user
* Network effect susah
* Kalah sama WA karena habit

### Risiko bisnis

* Monetisasi lama
* User cuma pakai pas butuh

### Risiko akademik

* Kalau output ga kelar → **PKL gagal**
* Pendanaan bisa hangus

⚠️ Ini BUKAN project “aman-aman aja”.

---

## Tapi… kenapa MASIH LAYAK diikutin?

Karena Launchpad:

* bukan lomba ide
* bukan cari profit cepat
* tapi **cari mahasiswa yang bisa bangun produk dari nol**

Dan project kamu:

* problem real
* scope realistis
* bisa MVP
* cocok buat tim kecil

---

## VERDICT AKHIR (JUJUR & REALISTIS)

### ✅ Layak diikutkan JIKA:

* Target kamu: inkubasi, PKL, pengalaman startup
* Kamu siap fokus 5 bulan
* Kamu framing sebagai **pilot kampus**

### ❌ Tidak layak JIKA:

* Kamu berharap langsung rame & cuan
* Kamu ga siap komit waktu
* Kamu pengen project santai

---
