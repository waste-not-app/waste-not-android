# Overview WasteNot Android

## Latar belakang

WasteNot terinspirasi dari **WasteNot**, situs pengelolaan sampah karya Tim Supernova yang meraih juara 1 kategori *Web Application Competition* pada PROXO National IT Competition 2024. Menurut [artikel School of Information Systems BINUS](https://sis.binus.ac.id/is/achievement/2-kategori-lomba-proxo-2024-berhasil-diraih-oleh-mahasiswa-sistem-informasi/), karya tersebut berfokus pada pengelolaan sampah yang efisien dan berkelanjutan dengan prinsip **Reduce, Reuse, Recycle (3R)**. Proyek ini mengadaptasi gagasan tersebut menjadi aplikasi Android; artikel itu tidak merinci fitur atau alur produk asli.

## Problem statement

Dalam kegiatan sehari-hari, pengguna dapat kesulitan mengetahui jumlah sampah yang mereka hasilkan, menentukan cara menangani suatu material, dan melihat apakah kebiasaan 3R mereka membaik. Tanpa tempat yang menyatukan catatan aktivitas, panduan, dan ringkasan, keputusan pengelolaan sampah sulit ditinjau kembali. Rumusan masalah ini adalah dasar rancangan aplikasi Android kami, bukan kutipan hasil riset pengguna atau klaim dari artikel lomba.

**Bagaimana membantu pengguna mencatat aktivitas pengelolaan sampah, memilih tindakan 3R yang sesuai, dan mengevaluasi kebiasaan mereka melalui satu aplikasi Android yang mudah digunakan?**

## Solusi Android yang dirancang

WasteNot Android dirancang sebagai alat pencatatan dan refleksi pribadi. Alur utamanya adalah pengguna masuk, mencatat aktivitas dengan kategori, metode penanganan, tanggal, serta berat **atau** jumlah benda, lalu melihat kembali catatan tersebut di riwayat. Panduan sampah membantu pengguna mencari cara penanganan material. Beranda dan statistik merangkum catatan menurut periode, kategori, dan metode agar perubahan kebiasaan lebih mudah terlihat. Metode *Trash* tetap dicatat agar gambaran aktivitas tidak hanya memuat tindakan 3R.

Aplikasi dikembangkan secara native dengan Kotlin, Jetpack Compose, dan Material 3. Rancangan produk selengkapnya ada di [PRD](design-kit-v1.3/PRD.md). Pemindaian sampah dan rekomendasi berbantuan AI masih termasuk rencana pengembangan lanjutan.

## Status implementasi

Saat dokumen ini dibuat, kode Android baru berisi fondasi satu Activity, tema Compose, satu rute navigasi, serta layar contoh yang menampilkan “Hello Android!”. Autentikasi, pencatatan sampah, riwayat, panduan, dashboard, statistik, dan fitur AI di atas masih merupakan **rancangan**, belum fitur yang berjalan di aplikasi.
