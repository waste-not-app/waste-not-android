# Panduan awal AI pada Android, 7 November 2026

Acuan backend `c1e81c25d2bd24fc5e8a010cfd72217560418756` ([ANDROID.md](https://github.com/waste-not-app/waste-not-backend/blob/c1e81c25d2bd24fc5e8a010cfd72217560418756/docs/ANDROID.md)) dan AI `baadd773aac01f7ef48c8beb15f62875c56f6b3f` ([HANDLING_GUIDANCE.md](https://github.com/waste-not-app/waste-not-ai/blob/baadd773aac01f7ef48c8beb15f62875c56f6b3f/docs/HANDLING_GUIDANCE.md)). Pin ini menggantikan snapshot kontrak pada checkpoint alignment sebelumnya; bukan klaim bahwa worker publik sudah mengaktifkan seluruh manifest terbaru.

## Perilaku S24/S25

NEEDS_CONTEXT dan UNAVAILABLE kini menampilkan langkah panduan awal serta sumber dari respons server. Judul membedakan penanganan awal/khusus; permintaan konteks, caution dan penanda prototipe tetap terlihat. Input manual, koreksi objek dan periksa kondisi tetap tersedia. Hasil tersebut tetap tidak menawarkan metode atau tombol penggunaan rekomendasi AI. Respons lama tanpa langkah masih memakai fallback sebelumnya.

Katalog terbaru berisi 85 pilihan, termasuk tujuh `other_*` per kategori. Daftar Android berasal dari server dan tetap memerlukan pilihan material/benda eksplisit. Penambahan katalog tidak mengubah kemampuan deteksi material. DTO yang ada mendukung langkah/sumber pada semua status; schema terbaru mengubah default versi policy dan contoh, tanpa menambah field atau status API. Versi yang benar-benar dikirim server tetap dipertahankan.

Android tetap hanya mengakses Go melalui HTTPS. UUID, korelasi ID/revision, validasi metode/sumber, konfirmasi pengguna, batas timeout dan aturan provenance tidak dilonggarkan. HTTP error bukan rekomendasi sukses. Panduan HTTP429 pada demo web AI tidak diterapkan sebagai fallback untuk error Go.

Manifest rollout AI memilih bundle v4 + COCO11s dengan 88 label. Android menerima metadata kelas/model dari server; manifest tersebut bukan bukti versi worker aktif atau akurasi material. Bukti live YOLO11n/Gemini sebelumnya tetap berlaku pada waktu dan checksum yang tercatat di [AI_LIVE_UI.md](AI_LIVE_UI.md).

## Verifikasi

- [Kedua bundle kontrak](policy08/paired-contracts.json): masing-masing 34 artifact cocok. Pemeriksaan memakai ekspor ref remote; checkout/WIP rekan backend dan AI tidak diubah.
- [Tujuh fixture transport](policy08/fixture-integrity.json) identik dengan contoh AI yang dipin. Tiga asset UI identik dengan snapshot transport terkait.
- [12 tes transport](policy08/transport-tests.xml) lulus, nol gagal/error/skip. Mencakup katalog85, status non-READY dengan langkah/sumber, metode tetap null dan tidak ada retry otomatis.
- Build native debug dan APK instrumentasi lulus dengan hot reload/dummy dinonaktifkan serta origin Go publik. [Hash sumber dan APK](policy08/source-build.json) mengikat hasil ke perubahan yang diperiksa.
- Review Standards: 0 temuan; Spec: 0 temuan. Review sumber tidak menggantikan pemeriksaan render.

Pada **8 November**, tiga metode native lulus pada API 37, 390×844 dp, font normal: [log 43,452 detik](policy08/native-ui/native-tests.log), [hasil runtime](policy08/native-ui/native-result.json), [hash APK/driver](policy08/native-ui/intent-driver-build.json). APK aplikasi tetap source `561e369`, SHA `8a35548c5dfb3b59c00c1af88c8fd1b5c9df3e597db67aec7da1ef1035680413`. Tiga metode mencakup non-READY dua tema, konfirmasi syarat CONDITIONAL dan pemisahan prototipe dari saran live yang dapat digunakan. Empat tautan sumber, empat tindakan periksa kondisi dan empat fallback manual teramati; metode/tombol penggunaan AI tidak muncul pada non-READY.

[16 PNG](policy08/native-ui/capture-manifest.json) memuat panel dan layar penuh pada dua status × dua tema × dua posisi scroll. Langkah panjang, sumber/penerbit/wilayah/tanggal dan kontrol bawah terbaca. Contoh: [panduan khusus gelap](policy08/native-ui/needs_context-dark-guidance.png), [sumber terang](policy08/native-ui/needs_context-light-sources.png), [panduan awal gelap](policy08/native-ui/unavailable-dark-guidance.png), [fallback sumber terang](policy08/native-ui/unavailable-light-sources.png). Fixture UI merupakan contoh kontrak prototipe; expiry disesuaikan hanya pada objek test agar layar dapat dirender. Ini tidak mengubah payload snapshot, TTL produksi atau perilaku expiry aplikasi, dan bukan panggilan provider live baru.

Percobaan sebelumnya tidak diterima: boot belum siap, root aksesibilitas belum tersedia pada capture, dan pengganti `LocalUriHandler` pada parent tidak menangkap handler window modal. Hierarchy menunjukkan Chrome benar-benar terbuka; klik sumber bukan kegagalan produk. Driver kini memantau intent HTTPS Android dan menahan peluncuran browser, sehingga tujuan persisnya diperiksa tanpa onboarding Chrome. Capture menunggu window aplikasi dan tetap menolak window lain. APK aplikasi tidak diubah untuk memperbaiki driver. Pada putaran diterima, dua dialog Pixel Launcher ditutup sebelum preflight menyatakan jendela sehat; tidak ada dialog sistem dalam frame hasil. Display dipulihkan setelah tes.

CI `561e369`, [run 42375674852](https://github.com/waste-not-app/waste-not-android/actions/runs/42375674852), meluluskan lint, unit, build semua varian serta gate paket/font pada kedua attempt. Upload APK/laporan masih ditolak kuota storage, termasuk pengulangan sesudah beberapa jam. Status keseluruhan tetap gagal dan PR belum di-merge. API artifact Android menunjukkan nol artifact pada refresh 8 November; itu tidak membuktikan kuota organisasi sudah pulih. GitHub [memperbarui perhitungan storage dalam 6–12 jam](https://docs.github.com/en/billing/concepts/product-billing/github-actions); pembacaan billing lebih lanjut memerlukan scope organisasi yang belum tersedia. Tidak ada perubahan billing atau pelonggaran gate.

Checkpoint ini tidak membuktikan seluruh screen/font/state, kamera fisik, model baru aktif atau penerimaan rilis. Prioritas screen dan navigasi pada [UI_FIRST_PLAN.md](../../UI_FIRST_PLAN.md) tetap berlaku.
