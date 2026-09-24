# WasteNot: Product & Design Kit

Dokumen produk 21 September 2026 · Design system 23 September 2026

Mulai dengan membuka **[design-system.html](design-system.html)** di browser. Tidak perlu build, instalasi dependency, atau koneksi internet. Pertahankan file CSS, JavaScript, dan folder `assets` di sebelah HTML. Jika memakai ZIP, ekstrak seluruh isinya dahulu.

## Isi paket

| File | Isi |
|---|---|
| [PRD.md](PRD.md) | Kebutuhan lengkap, scope, validasi, aturan metrik, acceptance, dan keputusan terbuka |
| [DESIGN.md](DESIGN.md) | Identitas WasteNot, warna terang/gelap, font, spacing, komponen, accessibility dan handoff Compose |
| [design-system.html](design-system.html) | Fondasi visual, 34 komponen, 20 layar/panel, dan prototype interaktif |
| [SCREEN_SPEC.md](SCREEN_SPEC.md) | S01–S20, D01–D04, C01–C34, field, aksi, dan state |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Android Compose, state/navigation, backend dan infrastruktur |
| [API.md](API.md) | Model data dan kontrak API usulan |
| [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) | Backlog empat minggu dan skenario pengujian aplikasi |
| [QA.md](QA.md) | Hasil pemeriksaan paket HTML dan batas validasinya |
| `assets/fonts/` | Plus Jakarta Sans variable font dan lisensi SIL OFL |
| `verify.cjs` | Pemeriksaan ulang otomatis menggunakan Node.js dan Playwright |

## Cara meninjau mockup

1. Buka Fondasi visual untuk melihat palet, font, spacing, dan radius.
2. Coba contoh kontrol di Komponen.
3. Buka Semua layar untuk galeri lengkap. Preview galeri statis; “Coba layar” membuka versi interaktif.
4. Di Coba alur, pilih layar dan state. Coba catatan baru, edit, hapus, pencarian panduan, filter, dan rentang statistik.
5. Ganti tema di navigasi dokumentasi. Gunakan Reset data simulasi untuk kembali ke data awal.

Tanggal simulasi dipatok **21 September 2026** agar contoh statistik konsisten dengan PRD: lima aktivitas, 1.400 g ditangani, dua benda dihindari, recycling rate 50%, dan 50 XP. Hari ini pada simulasi berisi 650 g ditangani. Tanggal ini tidak mengikuti jam perangkat.

Data prototype berada di memori dan hilang saat reload. Galeri dan cover adalah snapshot dataset awal, sehingga tidak ikut berubah ketika prototype diedit. Angka pada prototype Beranda/Riwayat/Statistik/Profil berubah bersama. Jangan masukkan credential atau data pribadi sungguhan.

## Arah desain yang dipilih

Hijau pinus untuk aksi, lime untuk progres, latar putih kehijauan, dan Plus Jakarta Sans. Desain disusun khusus untuk pencatatan dan kebiasaan 3R. Warna, font, dan UI marketplace dari contoh Titip.in tidak dipakai. Revisi v1.1 menambahkan gradasi pinus–lime, highlight lembut, dan dock navigasi kaca mengambang sesuai permintaan pengguna. Keputusan, fallback opak, dan handoff Compose tersedia di DESIGN.md.

## Batas prototype

Prototype adalah alat review UI. Auth dan respons server disimulasikan; tidak ada backend, request jaringan data pengguna, upload foto, penjadwalan notifikasi, atau akses lokasi. Foto hanya dipratinjau lokal. Lokasi memakai empty state karena belum ada dataset terverifikasi. Artikel panduan adalah contoh editorial yang perlu ditinjau sebelum menjadi konten produksi.

Foto, reminder, achievement, dan Maps tetap mengikuti prioritas PRD. Ketersediaan desain bukan bukti fitur tersebut sudah diimplementasikan. S10 (filter), S18 (lampiran), S20 (state), dan S06 (feedback) ditampilkan terpisah untuk handoff, bukan semuanya route Android baru.

## Pemeriksaan ulang

Browser modern dapat langsung membuka HTML. Untuk mengulangi pemeriksaan otomatis, gunakan Node.js dan Playwright yang sudah tersedia di lingkungan pengujian:

```sh
node verify.cjs
```

Jika Playwright berada di lokasi lain, isi `PLAYWRIGHT_PATH` dengan path package tersebut. `QA_DIR` opsional menentukan folder screenshot/report. Tanpa `QA_DIR`, hasil masuk folder sementara sistem bernama `wastenot-qa`. Tidak diperlukan Playwright untuk memakai prototype.

Paket ini tidak mengubah sumber PDF/HTML yang diberikan dan belum membuat project Android. Tim dapat menempatkan seluruh folder ini di `docs/product-design/` pada repository implementasi; tautan relatif tetap berfungsi.
