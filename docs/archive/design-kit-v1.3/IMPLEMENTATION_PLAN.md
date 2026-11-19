# WasteNot: Rencana Implementasi dan Pengujian

Versi 1.2 · 28 September 2026 · Core + AI extension · Rancangan implementasi

## 1. Cara membagi pekerjaan

Tentukan pemilik fungsi Android, backend/data, konten/desain, dan QA/demo. Satu orang boleh menangani lebih dari satu fungsi. Kerjakan satu alur vertikal memakai UI dan API nyata sebelum menyelesaikan seluruh layar secara paralel. Setiap tugas mempunyai hasil yang bisa diperiksa, bukan hanya persentase progres.

Dokumen acuan: [PRD](PRD.md), [API](API.md), [ARCHITECTURE](ARCHITECTURE.md), [SCREEN_SPEC](SCREEN_SPEC.md), [DESIGN](DESIGN.md).

## 2. Backlog per minggu

### Minggu 1: fondasi dan autentikasi

| ID | Pekerjaan | Dependensi | Bukti selesai |
|---|---|---|---|
| W1-01 | Setujui unit, makna Reduce, recycling rate, XP dan cut scope | Review PRD | Catatan keputusan tim |
| W1-02 | Buat Android project, pin toolchain/dependency | W1-01 | Build debug dan aplikasi kosong berjalan |
| W1-03 | Theme, fonts, typography, basic components | DESIGN | Preview terang/gelap + font scale |
| W1-04 | Navigasi lima tab, auth root, Back | W1-02 | Rekaman alur dan restore tab |
| W1-05 | Backend, DB migration, env debug, health | API | Migration ulang berhasil, readiness teruji |
| W1-06 | Register/login/refresh/logout + token store | W1-05 | Auth nyata, logout mencabut sesi |
| W1-07 | Screen S01–S03 + validasi dan error | W1-03,06 | Invalid credential dan offline tidak crash |

Gate minggu 1: satu akun dapat dibuat, login, logout, dan membuka sesi lagi. APK tidak memuat secret backend. Rahasia tidak muncul di log. Tema dan navigation bekerja pada perangkat demo.

### Minggu 2: tracking lengkap

| ID | Pekerjaan | Dependensi | Bukti selesai |
|---|---|---|---|
| W2-01 | Create log, constraint unit, idempotensi | W1-05 | Replay/paralel satu ID menghasilkan satu record |
| W2-02 | Editor, parser unit, draft restore, feedback | W2-01 | Input g/kg/count, retry tanpa kehilangan isian |
| W2-03 | List/detail/filter/pagination API dan UI | W2-01 | S07–S10 memakai data user |
| W2-04 | Update/delete/version conflict | W2-03 | Dua editor versi sama memicu konflik yang benar |
| W2-05 | Dashboard agregat dan invalidasi | W2-01,04 | Summary berubah setelah create/edit/delete |
| W2-06 | Test isolasi akun dan cache | Auth + semua log API | Akun B tidak melihat data akun A |

Gate minggu 2: alur create → read → update → delete lengkap; restart tidak menghilangkan catatan tersimpan; data A/B terpisah; dashboard tidak memakai hardcoded angka.

### Minggu 3: panduan dan evaluasi

| ID | Pekerjaan | Dependensi | Bukti selesai |
|---|---|---|---|
| W3-01 | Tinjau 12–20 artikel awal (target usulan) | Sumber konten | URL dan tanggal review nyata |
| W3-02 | Search/list/detail panduan | W3-01 | Keyword dan no-result teruji |
| W3-03 | Statistik, seri harian, kategori/metode | W2-01 | Dataset acuan menghasilkan angka PRD |
| W3-04 | Chart + alternatif teks + 7/30 hari | W3-03 | Pembaca layar mendapat unit dan nilai |
| W3-05 | XP dan level baseline | W2-04 | 9/10/11 catatan menguji batas level |
| W3-06 | Streak/achievement bila core sudah stabil | W3-05 | Hari kosong, backdate, delete/relock teruji |

Gate minggu 3: demo dari login sampai statistik berjalan pada server nyata; Guide telah direview; angka lintas layar cocok. Jika core gagal, minggu 4 difokuskan pada perbaikan core.

### Minggu 4: ketahanan dan demo

| ID | Pekerjaan | Kondisi | Bukti selesai |
|---|---|---|---|
| W4-01 | Regression test + perbaikan prioritas tinggi | Selalu | Matriks test terisi bukti |
| W4-02 | Accessibility, font scale, insets, tema | Selalu | TalkBack/keyboard/200% font checks |
| W4-03 | Build demo, konfigurasi staging, backup/restore | Selalu | Artifact dan prosedur restore |
| W4-04 | Reminder opt-in | Hanya jika gate core lulus | Permission denied, off, logout cancellation |
| W4-05 | Foto | Hanya jika kapasitas tersedia | Upload privat dan retry, bukan placeholder |
| W4-06 | Maps | Paling akhir dan ada data fasilitas | Fasilitas terverifikasi, no-permission fallback |
| W4-07 | Latihan presentasi dan catat keterbatasan | Selalu | Rekaman demo dan bug list final |

Jangan mengisi checklist opsional “selesai” hanya karena mockup tersedia. Jadwal ini tidak mengasumsikan semua stretch goal selesai dalam satu bulan.

## 3. Kasus uji penerimaan utama

| ID | Skenario | Hasil yang harus terjadi |
|---|---|---|
| AT-01 | Daftar akun baru lalu login ulang | Akun tersimpan; profile milik akun itu |
| AT-02 | Email duplikat/password pendek/salah | Error tepat; tidak tercipta sesi palsu |
| AT-03 | Tambah 250 g Plastik Recycle | Satu log, 10 XP, detail terbaca setelah restart |
| AT-04 | Tap simpan dua kali / retry timeout | Satu record dan 10 XP, bukan dua |
| AT-05 | Berat 0, negatif, terlalu besar, precision >3 kg | Ditolak client dan server |
| AT-06 | COUNT 2,5 atau weight+count bersamaan | Ditolak; tidak konversi unit diam-diam |
| AT-07 | Tambah 2 benda Reduce | avoided_count bertambah, handled totals tetap |
| AT-08 | Hanya Reduce, tanpa penanganan | Recycling rate null, bukan 0/100/NaN |
| AT-09 | Data campuran g dan benda | Ringkasan terpisah, tidak menjumlah unit |
| AT-10 | Dataset 5 baris PRD §9.4 | Hari ini 650 g, periode 1.400 g, 50%, 50 XP, streak 2 |
| AT-11 | Filter PLASTIC + RECYCLE | Hanya catatan memenuhi keduanya |
| AT-12 | Ganti filter saat request lama masih aktif | Respons lama tidak mengganti hasil baru |
| AT-13 | Edit 250 g menjadi 300 g | Total bertambah 50 g, XP tidak berubah |
| AT-14 | Delete satu log | Record hilang dari aktif; XP -10; statistik/streak dihitung ulang |
| AT-15 | Dua update dengan version lama | Update kedua 409; draf pengguna tetap tersedia |
| AT-16 | Read/PATCH/DELETE objek akun lain | 404, data tidak berubah |
| AT-17 | Logout A lalu login B | Cache/draf/stack A tidak muncul |
| AT-18 | Access expired, refresh valid | Satu refresh, request berlanjut sekali |
| AT-19 | Refresh revoked/expired | Masuk ulang, tidak refresh loop |
| AT-20 | Cari panduan, variasi kapital, hasil kosong | Hasil konsisten dan jalur hapus pencarian |
| AT-21 | Ganti rentang statistik 7/30 hari | Judul, tanggal, seri, dan summary sama periodenya |
| AT-22 | Tepat pergantian hari Asia/Jakarta | Hari ini, streak, dan rentang bergeser konsisten |
| AT-23 | Rotasi/process death pada draf | Draf nonrahasia pulih untuk user sama, password tidak |
| AT-24 | Font scale 200%, TalkBack | Field/CTA terbaca dan dapat dicapai |
| AT-25 | Dark/light + landscape + keyboard | Tidak ada teks hilang atau tombol tertutup |
| AT-26 | 1.000 log, pagination, error halaman berikut | Data awal tetap; retry tidak menduplikasi item |

Khusus update tanggal: tanggal lama yang dipertahankan boleh di luar jendela create; perubahan ke tanggal baru tetap harus dalam 30 hari terakhir. Uji kasus ini agar koreksi catatan lama tidak diblokir tanpa alasan.

## 4. Kasus opsi tambahan

- Reminder: off by default, permission denied, aktif lalu logout, hari sudah tercatat, waktu berubah, timezone tetap konsisten, tidak menjanjikan exact time.
- Foto: file valid/invalid, MIME palsu, file terlalu besar, image decoding limit, cancel picker, retry upload, unauthorized download, signed URL expired, orphan cleanup.
- Maps: permission ditolak, GPS mati, data fasilitas kosong, lokasi lama, provider gagal, pembukaan navigasi eksternal dengan pilihan pengguna.
- Achievement: threshold tepat, delete membuat relock, request berulang tidak menggandakan unlock event, tidak memberi nilai lingkungan ilmiah.

## 5. Pengujian otomatis secukupnya

Backend: domain test untuk rumus dan unit, integration test untuk authorization/idempotensi/transaksi. Android: ViewModel test untuk loading/save/error/refresh, parser jumlah, dan satu Compose UI journey login → tambah → riwayat dengan fake repository. End-to-end terpisah memakai staging nyata untuk membuktikan integrasi.

Jangan membuat test yang hanya menyalin implementation. Pilih data yang mematahkan asumsi: zero denominator, Reduce-only, mixed-unit, version conflict, multi-account, dan hasil POST yang responsnya hilang. Chart tidak memerlukan screenshot test untuk setiap data point; cukup check struktur/semantics dan review visual keadaan penting.

## 6. Matriks perangkat dan lingkungan

| Konfigurasi | Tujuan |
|---|---|
| Perangkat demo utama, versi OS dicatat | Bukti alur presentasi |
| Emulator compact 360 dp, font normal | Kepadatan minimum |
| Compact dengan font 200% | Reflow teks dan form |
| Landscape + keyboard tampil | Safe inset dan scroll |
| Tablet/resize minimal 600 dp | Rail/width constraint |
| API dengan notification runtime permission | Jika reminder dikerjakan |
| Koneksi normal, offline, lambat/timeout | Ketahanan data dan feedback |

Isi hasil nyata setelah pengujian. Paket dokumentasi tidak menetapkan angka latency/jank yang belum diukur.

## 7. Template catatan pengujian

```text
Tanggal:
Build / commit Android:
Build / commit backend:
Perangkat dan OS:
Lingkungan API:
Kasus uji:
Langkah:
Hasil diharapkan:
Hasil aktual:
Bukti (screenshot/log teredaksi):
Status: BELUM DIUJI / LULUS / GAGAL
Issue dan pemilik:
```

## 8. Skenario demo 5–7 menit (usulan)

1. Masuk dengan akun uji dan jelaskan bahwa catatan bersifat pribadi.
2. Tambah satu catatan Recycle valid; buka detail/riwayat dan tunjukkan persistensi setelah refresh.
3. Tambah Reduce berbasis benda; tunjukkan pemisahan dari berat ditangani.
4. Buka statistik dan jelaskan denominator recycling rate.
5. Cari panduan, buka sumber, lalu tunjukkan prefill catatan.
6. Tunjukkan XP/level dan satu keadaan gagal yang pulih tanpa duplikasi.
7. Logout dan login akun lain untuk membuktikan isolasi.

Hindari demo dengan angka palsu yang dipresentasikan sebagai data pengguna nyata. Akun uji dan catatan simulasi boleh dipakai jika identitasnya disebutkan. Jelaskan opsi yang belum diimplementasikan secara terbuka.

## 9. Release gate

- [ ] Semua FR Must dan baseline XP/level selesai atau ada perubahan scope yang disetujui.
- [ ] Tidak ada bug kehilangan data, kebocoran akun, crash blocker, atau request duplikat.
- [ ] API berjalan dengan TLS dan secret tidak masuk APK/Git/log.
- [ ] Konten panduan sudah ditinjau; sumber nyata dicantumkan.
- [ ] Backup/restore diuji untuk host bersama.
- [ ] Build dapat dipasang ulang dan alur demo diulang.
- [ ] README implementasi memuat setup, versi aktual, env names, dan batasan.
- [ ] Hasil test disertai bukti, tidak hanya checklist kosong.

Peluncuran publik membutuhkan pekerjaan tambahan yang belum tercakup demo: password reset, kebijakan privasi/retensi, alur penghapusan akun, operasi berkelanjutan, review keamanan, dan persyaratan kanal distribusi aktual.

## 10. Increment AI setelah core (revisi 28 September)

Permintaan terbaru mencakup computer vision, rekomendasi dan mockup tombol Catat/Scan pada navbar yang membuka kamera di bottom sheet. Empat minggu §2 tetap rencana core historis; estimasi tambahan AI disepakati setelah spike model/perangkat, tidak dianggap otomatis muat di minggu 4. Prioritaskan AI di atas Maps/reminder/foto lampiran jika itu fokus demo tim.

| Tahap | Pemilik fungsi | Hasil / gate sebelum lanjut |
|---|---|---|
| AI-01 Kontrak & taxonomy | AI + backend + konten | Pilih checkpoint kandidat, baca label aktual, audit lisensi dataset/bobot, tetapkan kelas pilot dan capability |
| AI-02 Camera sheet & manual | Android + desain | S21 capture/izin/close/Back; manual membuka S05; kamera berhenti saat background |
| AI-03 Detection service | AI + backend | Endpoint multipart, bounded worker, preprocessing/bbox, TTL/cleanup, laporan latency baseline |
| AI-04 Review & mapping | Android + backend | S22 multi-object satu pilihan, uncertain/unknown, koreksi kategori server-validated |
| AI-05 Guide & context | Konten + Android | S23 kondisi independent, panduan PUBLISHED versioned, unknown/no guide fallback |
| AI-06 Recommendation | Backend + AI | Provider terstruktur, schema + evidence + allowed methods + special handling, secrets/budget |
| AI-07 Integrasi log | Android + backend | S24 → S05 jumlah manual → create satu log; provenance, statistik, XP tidak ganda |
| AI-08 Evaluasi & pilot | QA + seluruh pemilik | Holdout report per kelas, safety/fallback, privasi, biaya, perangkat; keputusan go/no-go tertulis |

Fine-tuning setelah baseline taxonomy, lalu benchmark on-device/quantization bila latency/biaya/privacy memang menuntutnya. Keduanya tidak menjadi alasan menunda fallback manual. Camera scan dan lampiran foto adalah pekerjaan terpisah.

### Acceptance tambahan yang harus diuji pada implementasi

| ID | Skenario | Hasil wajib |
|---|---|---|
| AT-AI-01 | Tap action tengah dock dari lima tab | Satu sheet kamera; tab sebelumnya tetap; close mengembalikan fokus |
| AT-AI-02 | Izin denied/permanently denied, kamera tidak ada | Pesan relevan dan manual; tidak meminta berulang |
| AT-AI-03 | Capture, tinjau, retake, cancel | Tidak upload sebelum konfirmasi; file sementara dibersihkan |
| AT-AI-04 | Rotate/background/process death saat kamera | Tidak leak kamera, draf pulih, capture ulang bila file hilang |
| AT-AI-05 | Banyak bbox | Satu pilihan eksplisit, tidak auto-count atau batch save |
| AT-AI-06 | Threshold 0,49/0,50/0,699/0,70, unknown | State sesuai ambang kandidat; tidak mengklaim kepastian |
| AT-AI-07 | Class bottle tanpa material | Wajib konfirmasi; tidak auto-map ke PLASTIC |
| AT-AI-08 | Objek/kondisi berubah saat LLM request | Hasil lama diabaikan, rekomendasi diulang dengan konteks baru |
| AT-AI-09 | LLM JSON/enum/evidence tidak valid | Method null dan fallback; tidak disimpan sebagai keputusan |
| AT-AI-10 | Baterai/tajam/risiko khusus | Policy override, method null, panduan terkurasi, manual tersedia |
| AT-AI-11 | Reduce dari foto sampah | Tidak direkomendasikan otomatis; jalur manual untuk aktivitas pencegahan |
| AT-AI-12 | Network, timeout, overload, budget habis | Draf tetap, no retry loop; tracking manual berjalan |
| AT-AI-13 | Terima/ubah saran, isi 250 g, simpan dua kali | Satu log final, 10 XP; tidak memakai confidence sebagai statistik |
| AT-AI-14 | Owner silang/expired/linked recommendation | 404/410/409 tepat, no cross-account access, pilihan manual eksplisit |
| AT-AI-15 | File palsu, oversize, orientation, bbox invalid | Validasi tepat, tidak crash/decompression bomb, overlay sejajar |
| AT-AI-16 | Cleanup pada sukses, gagal, disconnect, crash | Tidak ada raw image di log/backup; scavenger memenuhi batas retensi |
| AT-AI-17 | TalkBack, 200% font, keyboard, small landscape | Daftar objek bisa dipilih tanpa bbox; CTA/manual dapat dicapai |
| AT-AI-18 | AI capability off | Catat manual tetap bisa dari action utama, auth/history tidak terganggu |

### Demo AI tambahan 2–3 menit

Dari navbar buka kamera sheet, ambil foto, konfirmasi kirim, pilih/koreksi objek, isi kondisi, periksa sumber, terima/ubah metode, isi jumlah sendiri, simpan, tunjukkan satu record dan perubahan XP. Ulang dengan kamera ditolak atau model tidak yakin untuk membuktikan manual fallback. Jika hanya memakai HTML, sebutkan hasil deteksi/rekomendasi adalah fixture simulasi; jangan mempresentasikan demo sebagai inference nyata.

### Gate AI sebelum pilot

- [ ] License/taxonomy/model/mapping/provider/guide versions tercatat.
- [ ] Holdout evaluation dan target kelas pilot disepakati; belum ada angka akurasi yang diasumsikan.
- [ ] AT-AI-01–18 mempunyai hasil dan bukti implementasi nyata.
- [ ] Kamera, upload dan cleanup diuji pada perangkat Android; web test tidak menggantikannya.
- [ ] Kebijakan special handling, penolakan invalid output dan manual fallback lulus.
- [ ] Anggaran, rate limit, timeout, observability redaction dan rollback diuji.
