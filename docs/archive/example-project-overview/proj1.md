Bagus banget kamu mikir begitu. Kalau mau pindah ke model lain seperti **Claude**, memang idealnya kamu kasih **dokumen konteks yang rapi** supaya dia langsung ngerti project tanpa harus baca chat panjang.

Aku bikinin **dokumen konteks lengkap** yang biasanya dipakai di startup/product team:

* Project Overview
* Problem Statement
* Product Vision
* Target Users
* Feature Scope
* User Flow
* PRD (Product Requirement Document)
* Technical Direction
* Roadmap

Formatnya **plain text** supaya gampang kamu paste ke Claude.

---

PROJECT OVERVIEW

Project Name:
Platform Jastip & Preloved Area Malang (temporary name)

Project Type:
Mobile application (Android-first)

Project Goal:
Membangun platform terpusat untuk menemukan dan membuka jasa titip (jastip) serta menjual barang preloved di area Malang, khususnya untuk mahasiswa.

Background:
Saat ini aktivitas jastip dan jual beli preloved di Malang banyak terjadi di grup WhatsApp, Telegram, atau media sosial. Informasi tidak terstruktur, sulit dicari kembali, tidak bisa difilter berdasarkan jarak, dan sering tercampur dengan chat lain.

Platform ini bertujuan menjadi **centralized discovery platform** untuk jastip dan preloved berbasis lokasi.

Important principle:
Aplikasi ini hanya menjadi **perantara informasi**, bukan marketplace penuh. Komunikasi dan transaksi dilakukan langsung melalui WhatsApp antar user.

---

PROBLEM STATEMENT

Masalah utama yang ingin diselesaikan:

1. Informasi jastip tersebar di banyak grup chat dan tidak terpusat.
2. Sulit menemukan jastip yang sedang aktif.
3. Tidak ada sistem pencarian berdasarkan jarak atau lokasi.
4. Jual beli preloved di grup chat sering tidak terstruktur.
5. Tingkat kepercayaan antar user rendah karena tidak ada identitas atau struktur informasi.

Target solusi adalah membuat platform yang:

* Menyediakan listing jastip yang sedang aktif
* Memungkinkan user mencari jastip berdasarkan lokasi
* Menyediakan ruang jual beli preloved yang lebih terstruktur
* Tetap menjaga kesederhanaan proses seperti di WhatsApp

---

PRODUCT VISION

Menjadi platform hyperlocal berbasis komunitas kampus yang memudahkan mahasiswa menemukan dan membuka jasa jastip serta menjual barang preloved secara cepat, terstruktur, dan terpercaya.

Scope awal produk difokuskan hanya pada area Malang dengan target utama mahasiswa.

---

TARGET USERS

Primary users:
Mahasiswa di kota Malang.

User types:

1. Jastip Seeker
   Mahasiswa yang ingin mencari jastip dari suatu tempat.

2. Jastip Provider
   Mahasiswa yang membuka jasa jastip.

3. Preloved Seller
   Mahasiswa yang ingin menjual barang bekas.

4. Preloved Buyer
   Mahasiswa yang ingin membeli barang preloved.

Future user segment:
Local stores atau UMKM yang ingin menjadi official store.

---

CORE FEATURES

Feature 1: Browse Jastip
User dapat melihat daftar jastip yang sedang tersedia.

Information shown:

* From location
* To location
* Deadline
* Distance
* Status

Action:
User dapat langsung menghubungi penyedia jastip melalui WhatsApp.

Feature 2: Open Jastip
User dapat membuat postingan jastip baru.

Input fields:

* From location
* Destination
* Deadline
* Notes (optional)

Feature 3: Request Jastip
User dapat membuat request jastip jika belum ada jastip yang tersedia.

User lain dapat mengambil request tersebut.

Feature 4: Preloved Marketplace
User dapat memposting barang preloved untuk dijual.

Information:

* Photo
* Item name
* Condition
* Price
* Description

Buyer akan diarahkan ke WhatsApp seller.

Feature 5: Location-based Discovery
User dapat melihat jastip berdasarkan jarak terdekat menggunakan GPS.

---

NAVIGATION STRUCTURE

Primary Navigation: Bottom Navigation

Tabs:
Home
Jastip
Preloved
Profile

Secondary Navigation:

Jastip Tab:

* Available
* Request Jastip

Preloved Tab:

* Items for Sale
* Items Wanted

Primary Action Button (FAB):

In Jastip tab:

* Open Jastip

In Preloved tab:

* Sell Item

---

USER FLOW EXAMPLES

Flow 1: Find Jastip

User opens app
User navigates to Home or Jastip tab
User browses available jastip
User opens jastip detail
User clicks "Contact via WhatsApp"
User chats directly with jastip provider

Flow 2: Open Jastip

User opens Jastip tab
User taps FAB
User fills form
User submits
Listing appears in jastip list

Flow 3: Sell Preloved Item

User opens Preloved tab
User taps FAB
User fills item details
User uploads image
User submits listing

---

BUSINESS MODEL

Initial stage:
Free platform to acquire users.

Future monetization:

1. Featured listing for jastip
   Users can boost their jastip listing to appear on top.

2. Official store partnership
   Local stores can join as official partners and get special badges.

3. Preloved posting fee
   Free limited posts per month, then small fee per post.

4. Local advertising
   Merchants around campus can promote their services.

Important note:
The platform does not handle payments between users.

---

TECHNICAL DIRECTION (INITIAL)

Frontend:
Android app (Kotlin)

Backend options:
Firebase or Supabase for rapid development

Possible services:
Authentication
Database
Image storage
Location service

Cloud option:
AWS can be used later if scaling is required.

---

ROADMAP

Month 1:
UI/UX design in Figma
Navigation and user flow design
Clickable prototype

Month 2:
Initial app development
Basic backend integration

Month 3:
MVP release for internal testing
Testing with students

Month 4:
Feature refinement
Early user acquisition

Month 5+:
Merchant partnership exploration
Monetization experiments

---

SUCCESS METRICS

Initial metrics:

Number of registered users
Number of active jastip listings
Number of preloved listings
User engagement rate

Later metrics:

Monthly active users
Listing conversion rate
Revenue from premium features

---

PROJECT STAGE

Current stage:
Early concept and product design.

Next milestone:
Create UI/UX prototype and validate with potential users.

---

Kalau kamu mau, aku juga bisa bikin **3 dokumen tambahan yang biasanya dipakai tim startup dan bakal bikin Claude jauh lebih efektif bantu kamu**, yaitu:

1. **System architecture draft** (biar AI bantu coding)
2. **Database schema draft**
3. **Feature backlog / task breakdown**

Kalau itu kamu punya, Claude bisa langsung bantu sampai level **generate backend + logic + API design**.