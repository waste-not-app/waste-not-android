# Scan: pasangan tema dan akhir scroll

Checkpoint visual 7 November 2026, sesudah prioritas screen ditegaskan kembali. Pengujian utama memakai ponsel Android API 37, 390×844 dp, font 100%, minSdk instalasi 30 dan hot reload mati.

## Perbaikan dan bukti

Pilihan material, jenis benda, kondisi dan metode pada scan kini memiliki tinggi minimum **56 dp**, mengikuti input pada DESIGN.md. Target ini menjaga konsistensi dengan form utama dan ruang sentuh ketika label berada di atas pilihan. Palet pine/lime, Plus Jakarta Sans, indikator tahap, surface sheet opak dan kartu ringkasan tetap mengikuti kit; tidak menambah gaya atau dependensi baru.

`ScanSheetTest.scanDesignViewportsLightAndDark` merender lima keadaan dalam dua tema: kamera sebelum aktivasi, foto, objek dengan material/jenis dipilih, kondisi awal dan saran. Setiap keadaan memiliki capture awal dan akhir scroll, masing-masing berupa panel serta display penuh: **40 PNG**, satu metode preview lulus dalam **34,711 detik**. Tinggi pilihan Kebersihan diperiksa minimal 56 dp. Akhir scroll mencapai Ambil ulang, Koreksi objek atau Periksa kondisi benda, bukan hanya tombol utama di atasnya.

Foto PET adalah aset CC0 yang sama dengan [fixture Android](../../../app/src/androidTest/assets/ai/README.md). Skor, kotak objek dan saran merupakan **data contoh terkontrol**, diberi label Mode demo. Kamera asli dirender sebelum aktivasi; preview ini tidak meminta izin, mengambil foto, mengirim request jaringan atau menyimpan catatan. Callback preview tidak menjalankan operasi produk.

Manifest [build](ui-preview/scan-themes/build.json) mengikat APK aplikasi/penguji serta hash sumber; source utama berasal dari bd5f278 dengan perubahan tinggi input ini. [Hasil dan hash 40 PNG](ui-preview/scan-themes/result.json), [log native](ui-preview/scan-themes/native.log), serta konfigurasi [sebelum](ui-preview/scan-themes/display-before.json)/[sesudah](ui-preview/scan-themes/display-restored.json) tersimpan bersama gambar. Semua konfigurasi display yang diubah dipulihkan. Frame display diperoleh dari screencap; panel dari window Compose yang aktif.

## Galeri

| Keadaan | Terang awal / akhir | Gelap awal / akhir |
|---|---|---|
| S21 Kamera sebelum aktivasi | [Awal](ui-preview/scan-themes/S21-camera-light-panel.png) · [Akhir](ui-preview/scan-themes/S21-camera-light-end-panel.png) | [Awal](ui-preview/scan-themes/S21-camera-dark-panel.png) · [Akhir](ui-preview/scan-themes/S21-camera-dark-end-panel.png) |
| S21 Tinjau foto | [Awal](ui-preview/scan-themes/S21-photo-light-panel.png) · [Akhir](ui-preview/scan-themes/S21-photo-light-end-panel.png) | [Awal](ui-preview/scan-themes/S21-photo-dark-panel.png) · [Akhir](ui-preview/scan-themes/S21-photo-dark-end-panel.png) |
| S22 Objek terpilih | [Awal](ui-preview/scan-themes/S22-light-panel.png) · [Akhir](ui-preview/scan-themes/S22-light-end-panel.png) | [Awal](ui-preview/scan-themes/S22-dark-panel.png) · [Akhir](ui-preview/scan-themes/S22-dark-end-panel.png) |
| S23 Kondisi awal | [Awal](ui-preview/scan-themes/S23-light-panel.png) · [Akhir](ui-preview/scan-themes/S23-light-end-panel.png) | [Awal](ui-preview/scan-themes/S23-dark-panel.png) · [Akhir](ui-preview/scan-themes/S23-dark-end-panel.png) |
| S24 Saran contoh | [Awal](ui-preview/scan-themes/S24-light-panel.png) · [Akhir](ui-preview/scan-themes/S24-light-end-panel.png) | [Awal](ui-preview/scan-themes/S24-dark-panel.png) · [Akhir](ui-preview/scan-themes/S24-dark-end-panel.png) |

Acuan kit: [kamera](scan-reference-camera-light.png), [kondisi](scan-reference-context-dark.png), dan [saran](scan-reference-recommendation-light.png). Konteks tambahan dari kontrak backend dipertahankan, termasuk rencana penggunaan ulang dan penerimaan pengelola yang independen. Layout scroll menampung pertanyaan ini; kolom tidak dihapus agar tinggi halaman menyerupai contoh ringkas.

Review gambar memeriksa hierarki judul, palet dua tema, indikator, field, CTA serta footer manual. Konten yang terpotong pada awal area scroll saat posisi akhir adalah bagian sebelum posisi gulir; aksi terakhir dan footer tetap terlihat. Kondisi awal dan saran kini memiliki gambar seluruh ujung konten.

## Batas checkpoint

Ini menutup celah pasangan preview normal S21–S24, bukan seluruh penerimaan UI atau integrasi. Izin/preview kamera aktif di tema gelap, dropdown terbuka, kondisi tambahan/bersyarat, selected/pressed/focus, teks besar dan pergantian tema melalui pengaturan masih mengikuti review berikutnya. [Bukti AI nyata](AI_LIVE_ANDROID.md) adalah checkpoint terpisah dan tidak berasal dari gambar demo ini. Pengujian utama berikutnya tetap pada perangkat Android baru sesuai goal terbaru.
