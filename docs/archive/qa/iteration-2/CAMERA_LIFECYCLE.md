# Kamera siap, pemulihan izin dan pelepasan preview

8 November 2026. `ScanCamera` pada commit `1007fe4`, yang ikut APK produk `e499511`, menunggu preview STREAMING sebelum mengizinkan foto. Ketika kembali dari Settings dengan izin yang sudah diberikan, pesan penolakan dan aksi Settings dibersihkan; pengguna tetap harus memilih Aktifkan kamera. Preview serta observer dilepas saat kamera keluar dari komposisi.

Preview memakai mode COMPATIBLE, dengan bidang gelap dan radius16dp. [Kontrak PreviewView.StreamState](https://developer.android.com/reference/androidx/camera/view/PreviewView.StreamState) menjelaskan hubungan mode tersebut dengan first frame. Kamera yang belum mengalir menunjukkan “Menyiapkan kamera…” dan tombol nonaktif. Error kamera kritis memakai fallback CAMERA yang sudah ada; error recoverable tetap ditangani CameraX.

## Bukti native

APK aplikasi yang benar-benar terpasang pada seluruh tes sesudah perubahan:

```text
d0301632d89f950e20b1bebbb1811a22931d72dca7880f684e24e34c68e0af49
```

API37, 390×844dp, font100%. Empat eksekusi native lulus: dua skenario, masing-masing tema terang dan gelap.

| Skenario | Yang diverifikasi |
|---|---|
| Izin Settings | Tolak dua kali → aksi Settings → izin benar-benar diberikan → lifecycle CREATED/RESUMED → Aktifkan kembali, aksi Settings hilang, preview tetap tidak dibuat. Input manual menutup sheet dan memanggil callback tepat sekali, tanpa foto. |
| Preview/foto/close | Preview CameraX benar-benar STREAMING dan bitmap bukan bidang satu warna. Unbind mengubahnya menjadi IDLE, tombol foto nonaktif dan status persiapan terlihat. Rebind memulihkan stream; capture menghasilkan JPEG valid dan satu callback. Masuk ke tinjauan foto melepas preview. Sesi berikutnya dibuka, stream diperiksa lagi, lalu close melepas controller dan observer. |

Intent ke Settings diperiksa URI package-nya dan diintersep oleh monitor test; perubahan izin Android dan lifecycle Activity nyata. Ini tidak mengklaim klik menembus halaman Settings sistem. Fixture memakai `ScanSheet`/`ScanCamera` asli dengan state callback sederhana, tanpa `MainNavigation`, upload, deteksi atau generasi provider.

[Hasil izin](camera-lifecycle/after-permission/result.json) dan [log terang](camera-lifecycle/after-permission/permission-light.log) dan [log gelap](camera-lifecycle/after-permission/permission-dark.log) memakai APK penguji `d5196ab4969ddff5aa2b75f3a12fb5f12f9382dc687025f3072c679f03faa4a3`. [Hasil preview](camera-lifecycle/after-preview/result.json), [log terang](camera-lifecycle/after-preview/preview-light.log) dan [log gelap](camera-lifecycle/after-preview/preview-dark.log) memakai `9377b24fcbc83ca25a3a3ead26fbd426d78be5ff8bfbb8fa78b1154bd8c443de` sesudah penguji memindahkan pembacaan API CameraX ke main thread.

Kedua APK penguji berbeda dan dipin terpisah. APK aplikasi tetap identik. Perubahan penguji hanya memperbaiki akses footer tetap serta aturan thread platform, tanpa melemahkan pemeriksaan readiness, JPEG atau release. [Manifest penguji izin](camera-lifecycle/camera-test-build.json) dan [penguji preview](camera-lifecycle/camera-main-thread-test-build.json) menyimpan checksum source.

Sesudah tiap fase, ukuran/density/font serta status/flag izin dipulihkan dan dibaca kembali. Direktori foto sementara fixture dibersihkan. Pelepasan controller/observer dibuktikan oleh tes; cleanup foto pada alur bisnis `ScanViewModel` tetap mengikuti bukti integrasi tersendiri.

## Frame dan batas baseline

[Galeri](camera-lifecycle/index.md) memuat **24 PNG sesudah perubahan**, enam state pada dua tema, serta empat PNG baseline izin tema terang. Delapan frame izin di galeri sesudah berasal dari fase izin; 16 frame lainnya berasal dari fase preview. [Manifest 28 PNG](camera-lifecycle/capture-manifest.json) mencatat hash file asli.

Baseline APK `55448ce0bcbd0e2aad44a811c5961f55e4e33510cff7f081641b7954686328f9` sudah memperoleh izin kamera, tetapi [capture dan status Android](camera-lifecycle/before-permission/permission-granted-return-light.json) masih memperlihatkan aksi Settings/pesan penolakan. Pasangan frame menunjukkan perbaikannya pada APK saat ini.

Baseline tidak diklaim sebagai empat kegagalan produk yang seragam: [percobaan izin](camera-lifecycle/trials/permission-footer.log) berhenti pada kesalahan penguji yang mencoba menggulir footer tetap, dan [percobaan preview](camera-lifecycle/trials/preview-main-thread.log) berhenti karena getter CameraX dipanggil di luar main thread. Kedua percobaan tersebut bukan penerimaan penuh test case maupun bukti bahwa preview lama telah diuji sampai akhir. Tes sesudah perubahan memperbaiki harness dan lulus seluruh assertion yang disebutkan di atas.

Frame IDLE boleh tetap menampilkan gambar terakhir; indikator state dan tombol tidak menyebutnya siap. Tinjauan foto menampilkan JPEG hasil capture virtual scene. Ini bukan pengujian kamera fisik, akurasi model atau jalur AI end-to-end baru. CI, pagination/filter riwayat A18, DataStore A30 dan regresi akhir seluruh alur tetap memiliki status terpisah.
