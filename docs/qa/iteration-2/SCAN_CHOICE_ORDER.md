# Urutan pilihan hasil scan

9 November 2026, S22, A42/#81. Pengguna mengonfirmasi bahwa prioritas berlaku untuk daftar kandidat pada foto dan menu Jenis objek.

## Perubahan

Kandidat yang eligible ditampilkan menurut skor deteksi server secara menurun. Skor yang sama mempertahankan urutan respons; kandidat dengan status manual atau tidak didukung tetap mengikuti fallback sebelumnya. Pemilihan awal mengikuti kandidat tertinggi, tetapi pengguna dapat menggantinya. Respons asli, ID, bbox dan band server dipertahankan; skor tidak disebut sebagai akurasi.

Menu Jenis objek menampilkan key saran kandidat terpilih menurut urutan server, dibatasi katalog dan material yang dikonfirmasi pengguna. Key tidak dikenal, duplikat dan material lain diabaikan; sisa katalog mengikuti urutan semula. Tanpa saran yang cocok, urutan katalog dipakai. Ketika opsi berubah, posisi scroll menu kembali ke atas. Pengguna tetap wajib memilih material/jenis objek dan mengonfirmasi; koreksi manual tersedia.

Sebelumnya, menu sudah mengelompokkan saran di atas pilihan lain, tetapi urutan antar saran mengikuti katalog. Perubahan ini memperbaiki urutan tersebut dan posisi menu, bukan memperkenalkan prioritas dasar dari nol. Token, tipografi dan komponen kit dipertahankan tanpa dekorasi atau gerak baru.

## Baseline dan verifikasi

Baseline unit gagal karena daftar kandidat masih mengikuti respons yang tidak terurut. Setelah perbaikan, pengujian membuktikan urutan menurun, tie stabil, respons/band asli tetap sama, material belum dipilih dan kandidat lain tetap bisa dipilih.

Baseline native memakai kandidat yang sudah diurutkan tetapi menu jenis objek lama. Metode tema terang gagal tepat pada `Server hint order must put HDPE before PET`: respons menyarankan HDPE kemudian PET, sementara menu menampilkan PET dahulu. APK penguji yang sama digunakan untuk pembanding akhir; fingerprint sumber dan APK ada pada manifest build.

**271 debug unit tests lulus, nol failure/error/skip.** Enam metode baru khusus katalog mencakup saran kosong/tidak cocok, key asing/duplikat/material lain, pergantian kandidat, koreksi manual dan katalog dinamis 85 item termasuk panduan belum tersedia. Lint, debug/staging build, SDK minimum API30, adjustResize, lima bobot Jakarta Sans/OFL dan pemeriksaan adapter backend nyata lulus.

**Dua metode native lulus pada percobaan akhir pertama**, API37, 390×844 dp/font100%, tema terang dan gelap. Sepuluh PNG asli ditinjau: urutan kandidat, urutan saran, koreksi manual ke item paling bawah, pemulihan posisi setelah material diganti dan saran baru setelah kandidat diganti. Driver memverifikasi hash APK terpasang, tidak menemukan ANR saat preflight, lalu memulihkan ukuran/density/font/night mode. Emulator task dihentikan setelah selesai.

| Perilaku | Terang | Gelap |
|---|---|---|
| Kandidat skor tinggi di atas, material belum dipilih | [Frame](scan-choice-order/final/captures/candidates-light.png) | [Frame](scan-choice-order/final/captures/candidates-dark.png) |
| HDPE sebelum PET sesuai urutan respons | [Frame](scan-choice-order/final/captures/suggested-types-light.png) | [Frame](scan-choice-order/final/captures/suggested-types-dark.png) |
| Koreksi manual tetap terpilih | [Frame](scan-choice-order/final/captures/manual-correction-light.png) | [Frame](scan-choice-order/final/captures/manual-correction-dark.png) |
| Ganti material mengembalikan menu ke atas | [Frame](scan-choice-order/final/captures/material-reset-top-light.png) | [Frame](scan-choice-order/final/captures/material-reset-top-dark.png) |
| Ganti kandidat memperbarui saran | [Frame](scan-choice-order/final/captures/changed-candidate-light.png) | [Frame](scan-choice-order/final/captures/changed-candidate-dark.png) |

Pembanding menu lama: [PET sebelum HDPE](scan-choice-order/baseline/captures/suggested-types-light.png). [Baseline kandidat](scan-choice-order/candidate-baseline.xml), [baseline native](scan-choice-order/baseline/native.log), [hasil native akhir](scan-choice-order/final/native-result.json), [271 unit](scan-choice-order/unit-result.json), [lint/build](scan-choice-order/final-build-retry.log). Kesalahan import ganda pada kompilasi pertama diperbaiki sebelum build akhir; log awal disimpan di direktori QA lokal.

## APK dan penyerahan

| Paket native | SHA-256 |
|---|---|
| Debug | `277ab6c0891be2c3013b848091cadcd26fd47455cdb034d6f2921e484b05ec23` |
| Staging | `7b3c41ef3a9876df7f931dd03cad1fadd759b59534f273db128fd8543bac96be` |
| Penguji, sama untuk sebelum/sesudah | `2f866581d6601a8c49bbe8fa98f44d352b4d7574baf1d0610fff3c4c2473c172` |

[Manifest akhir](scan-choice-order/final/build-manifest.json). Staging 36.408.131 byte, package terpisah, tidak debuggable dan signature v2 diverifikasi. Upgrade dari APK `07ba9dae` tanpa clear data dan startup di luar instrumentation lulus hingga [form masuk](scan-choice-order/staging-startup.png); [hasil](scan-choice-order/staging-result.json). Ini pemeriksaan paket/startup, bukan pengulangan seluruh journey pada staging. [Pointer APK lokal](scan-choice-order/delivery.json) diperbarui setelah penerimaan dengan versi sebelumnya tetap diarsipkan.

Self-review mengikuti code-reviewer: eligibility/band server, stabilitas urutan, preservasi identitas, material wajib, koreksi manual, scroll dan isolasi fixture diperiksa; tidak ditemukan penghalang dalam scope perubahan. Gate Anti Slop selama implementasi: Hard/Purpose/Craft lulus untuk urutan dan interaksi yang diuji, Liveliness mempertahankan kit ENERGY2/RHYTHM2/MOTION1. Tidak ada klaim penerimaan global dari checkpoint ini; hasil CI dilaporkan terpisah setelah push.

## Batas bukti

Pengujian native ini merender ScanSheet asli melalui state dan callback terkontrol pada source pengujian, tanpa akun atau panggilan backend/AI baru. Ini membuktikan pilihan/menu, bukan pengambilan foto, transform bbox, akurasi model, spoken TalkBack atau seluruh alur aplikasi. Bukti kamera/backend nyata tersedia terpisah pada [AI_LIVE_UI.md](AI_LIVE_UI.md). Semua APK aplikasi mempertahankan adapter remote; fixture tidak masuk ke mode aplikasi.

A42 tetap In progress untuk kriteria lain. Checkpoint ini tidak menyatakan seluruh goal atau penerimaan visual selesai. Room/offline-first juga belum diterapkan; repository aktivitas masih memakai backend langsung.
