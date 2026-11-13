# WasteNot: Product & Design Kit

Product & design kit v1.4 · 13 November 2026

Mulai dengan membuka **[design-system.html](design-system.html)** di browser. Tidak perlu build, instalasi dependency, atau koneksi internet. Pertahankan file CSS, JavaScript, dan folder `assets` di sebelah HTML. Jika memakai ZIP, ekstrak seluruh isinya dahulu.

## Isi paket

| File | Isi |
|---|---|
| [AI_SPEC.md](AI_SPEC.md) | Pipeline YOLO/Guide/LLM, dataset, validasi, privasi, scope dan evaluasi |
| [sources/AI_PLAN_ORIGINAL.md](sources/AI_PLAN_ORIGINAL.md) | Salinan plan tim sebagai sumber, bukan spesifikasi implementasi final |
| [PRD.md](PRD.md) | Kebutuhan lengkap, scope, validasi, aturan metrik, acceptance, dan keputusan terbuka |
| [DESIGN.md](DESIGN.md) | Identitas WasteNot, warna terang/gelap, font, spacing, komponen, accessibility dan handoff Compose |
| [design-system.html](design-system.html) | Fondasi visual, 39 komponen, 25 layar/panel, dan prototype interaktif |
| [SCREEN_SPEC.md](SCREEN_SPEC.md) | S01–S25, D01–D04, C01–C39, field, aksi, dan state |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Android Compose, state/navigation, backend dan infrastruktur |
| [API.md](API.md) | Model data dan kontrak API usulan |
| [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) | Backlog empat minggu dan skenario pengujian aplikasi |
| [QA.md](QA.md) | Hasil pemeriksaan paket HTML dan batas validasinya |
| `assets/fonts/` | Plus Jakarta Sans variable font dan lisensi SIL OFL |
| `verify-ai.cjs` | Uji sheet, kamera virtual, koreksi hasil, skenario gagal dan manual fallback |
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

Hijau pinus untuk aksi, lime untuk progres, latar putih kehijauan, dan Plus Jakarta Sans. Desain disusun khusus untuk pencatatan dan kebiasaan 3R. Warna, font, dan UI marketplace dari contoh Titip.in tidak dipakai. Revisi v1.1 menambahkan gradasi pinus–lime, highlight lembut, dan dock navigasi kaca mengambang sesuai permintaan pengguna. Revisi v1.2 menambah action Catat/Scan di tengah atas dock dan camera bottom sheet dengan fallback manual. Keputusan, fallback opak, dan handoff Compose tersedia di DESIGN.md.

## Cara mencoba AI scan

Buka Beranda pada Coba alur, tekan Catat/Scan di tengah atas dock. Bottom sheet menyediakan Aktifkan kamera, ambil foto, scan simulasi, serta Input manual. Gunakan contoh simulasi untuk mencoba tanpa kamera. Pilih skenario satu/multi-object, skor rendah, unknown, special handling, jaringan atau LLM gagal; periksa objek/kondisi/metode, lalu isi jumlah dan simpan. Kamera membutuhkan dukungan browser dan izin; localhost/HTTPS dianjurkan. Tidak ada upload, YOLO atau LLM nyata di HTML.

## Batas prototype

Prototype adalah alat review UI. Auth dan respons server disimulasikan; tidak ada backend, request jaringan data pengguna, upload foto, penjadwalan notifikasi, atau akses lokasi. Foto hanya dipratinjau lokal. Lokasi memakai empty state karena belum ada dataset terverifikasi. Artikel panduan adalah contoh editorial yang perlu ditinjau sebelum menjadi konten produksi.

Foto, reminder, achievement, dan Maps tetap mengikuti prioritas PRD. Ketersediaan desain bukan bukti fitur tersebut sudah diimplementasikan. S10 (filter), S18 (lampiran), S20 (state), dan S06 (feedback) ditampilkan terpisah untuk handoff, bukan semuanya route Android baru.

## Pemeriksaan ulang

Browser modern dapat langsung membuka HTML. Untuk mengulangi pemeriksaan otomatis, gunakan Node.js dan Playwright yang sudah tersedia di lingkungan pengujian:

```sh
node verify.cjs
node verify-ai.cjs
```

Jika Playwright berada di lokasi lain, isi `PLAYWRIGHT_PATH` dengan path package tersebut. `QA_DIR` opsional menentukan folder screenshot/report. Tanpa `QA_DIR`, hasil masuk folder sementara sistem bernama `wastenot-qa`. Tidak diperlukan Playwright untuk memakai prototype.

Paket ini tidak mengubah sumber PDF/HTML yang diberikan dan belum membuat project Android. Tim dapat menempatkan seluruh folder ini di `docs/product-design/` pada repository implementasi; tautan relatif tetap berfungsi.

## Perubahan v1.2 dan keputusan terbuka

Semua dokumen diperluas mengikuti plan tim dan arahan bottom sheet pengguna. Baseline AI memakai server inference privat, lima tab dipertahankan, jumlah diisi manual, Reduce tidak disimpulkan dari foto. Model/dataset/provider/hardware/biaya dan ambang evaluasi final harus dipilih tim. Uji web tidak membuktikan kualitas model atau implementasi Android. Sumber asli tetap disertakan; konflik teknis dijelaskan dalam AI_SPEC, bukan menyalin seluruh asumsi sumber sebagai fakta.

## Penyempurnaan v1.3

Preview S21–S25 kini menampilkan komponen sheet lengkap, indikator tahap, framing kamera, ilustrasi objek, kondisi dan review saran. Fallback manual tetap terlihat di footer sheet; pengaturan demo disembunyikan dalam disclosure. Koreksi mempertahankan isian, draf manual dilindungi, dan perpindahan dari kamera ke contoh simulasi menghentikan stream. Aturan produk/API v1.2 tetap berlaku.

## Penyempurnaan v1.4

Mockup dan `DESIGN.md` dirapikan memakai pendekatan **iOS Inset-Grouped + Liquid Glass** dengan pemangkasan teks inline: kartu statistik dan baris daftar dikelompokkan dalam container solid ber-radius konsisten, header/dock mengambang memakai efek Liquid Glass (`backdrop-filter: blur(22px) saturate(1.8)` + specular rim highlight) dengan konten mengalir di belakangnya, kontrol segmented/switch diperjelas, dan penjelasan metodologi panjang dipindah ke disclosure `(i)`. Aturan produk/API dan seluruh 25 layar (`S01–S25`) serta 39 komponen (`C01–C39`) tetap utuh.
