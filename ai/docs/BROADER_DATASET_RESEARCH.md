# Perluasan dataset deteksi objek WasteNot

Pemeriksaan: **20 Oktober 2026**. Ini mencatat riset sebelum eksperimen. Tindak lanjut permintaan pengguna tersedia pada [training Broad Waste aktual](BROAD_WASTE_TRAINING.md), dengan sumber yang akhirnya dapat dipakai dan keterbatasan datanya.

## Kondisi proyek

Saat riset ini dibuat, checkpoint hasil fine-tuning mempunyai empat keluaran: aluminium can, glass bottle, HDPE bottle, dan PET/plastic bottle. Demo biasa tetap memakai checkpoint COCO dengan 80 kelas; katalog saat itu berisi 69 pilihan benda/material, yang tidak berarti checkpoint khusus sudah memiliki 69 kelas. Perbedaan tersebut sudah didokumentasikan dalam [README AI](../README.md) dan [riset dataset pertama](DATASET_RESEARCH.md).

## Sumber primer yang diperiksa

### TACO: cocok untuk menambah variasi sampah, perlu kurasi

TACO menyediakan anotasi COCO untuk sampah dalam konteks. Repo membedakan anotasi utama dari `annotations_unofficial.json` yang belum ditinjau penulis; penulis juga memperingatkan banyak kelas berisi sedikit anotasi. [Repo penulis](https://github.com/pedropro/TACO).

Paper melaporkan 1.500 gambar, 4.784 anotasi, 60 kategori dalam 28 superkategori. Foto sebagian besar berasal dari telepon dan penulis menyebut foto menggunakan lisensi bebas. Paper mengakui material botol plastik/kaca bisa sulit dibedakan secara visual. Angka ini adalah versi paper, bukan janji jumlah data baru pada situs kontribusi. [Paper asli](https://arxiv.org/html/2003.06975v1).

Pemeriksaan langsung metadata utama pada tanggal di atas menghasilkan jumlah yang sama. Contoh kelas/instance yang relevan: Normal paper 82, Corrugated carton 64, Drink carton 45, Paper cup 67, Disposable plastic cup 104, Glass cup 6, Single-use carrier bag 61, Disposable food container 38, Foam food container 15, Tupperware 4, dan Battery 2. Taksonomi tersebut tidak menyediakan kelas mug keramik tersendiri. Metadata memiliki `licenses: []`; nilai `image.license` adalah null pada 715 gambar, `CC` pada 319, dan `ODBL (c) OpenLitterMap & Contributors` pada 466. Nilai ini belum menyebut jenis/versi lisensi foto yang cukup rinci untuk seluruh gambar. SHA-256 metadata yang diperiksa: `ac1ec605c9e1fcda767970de3457c69b4a43b62c02aca8c4c80f51d1b5ae8449`. [Metadata resmi](https://raw.githubusercontent.com/pedropro/TACO/master/data/annotations.json).

**Keputusan yang disarankan:** jadikan TACO pelengkap untuk kertas, karton, gelas sekali pakai, kantong, pembungkus, dan wadah. Audit hak foto/atribusi dari sumber setiap gambar; MIT pada toolkit tidak dianggap otomatis melisensikan seluruh foto. Jangan mengandalkan dua instance baterai atau enam glass cup sebagai dasar kualitas kelas tersebut. Dahulukan anotasi utama, kemudian inspeksi bbox dan label secara visual.

### Open Images V7: penting untuk mug dan objek rumah tangga

Halaman resmi mencatat sekitar 1,9 juta gambar dengan bbox dalam 600 kelas. Anotasi menggunakan CC BY 4.0; gambar dicantumkan sebagai CC BY 2.0 dengan peringatan resmi untuk memverifikasi lisensi setiap gambar. [Fakta dan lisensi resmi](https://storage.googleapis.com/openimages/web/factsfigures_v7.html).

Daftar kelas bbox resmi mencakup Mug (`/m/02jvh9`), Coffee cup (`/m/02p5f1q`), Plastic bag (`/m/05gqfk`), Tin can (`/m/02jnhm`), Bottle (`/m/04dr76w`), Box (`/m/025dyy`), dan Container (`/m/011q46kg`). Halaman unduhan menyediakan alur subset kelas. [Daftar label resmi](https://storage.googleapis.com/openimages/v5/class-descriptions-boxable.csv), [unduhan dan subset](https://storage.googleapis.com/openimages/web/download_v7.html).

**Keputusan yang disarankan:** prioritaskan Mug/Coffee cup untuk masalah cangkir pada demo, serta kelas umum yang dapat dipetakan dengan jelas. Label Box tidak membuktikan bahan kardus; Container tidak membuktikan wadah makanan. Periksa hierarki kelas dan kelengkapan anotasi per gambar sebelum menggabungkan sumber. Objek target yang terlihat tetapi belum diberi bbox perlu diaudit agar tidak menjadi contoh background yang salah.

### SortWaste: variasi material dan clutter, domain berbeda

Penerbit menyediakan 5.261 gambar dan 87.252 bbox dalam delapan kelas: HDPE, PET, PET Oil, Mixed Soft Plastic, Mixed Rigid Plastic, ECAL, Cardboard, dan Metal. Data diambil dari atas conveyor; format split tersedia dalam COCO dan YOLO. Pada situs dan repo resmi yang diperiksa, tidak ditemukan pernyataan lisensi dataset eksplisit. Ini berarti status pemakaian belum dipastikan oleh riset ini, bukan klaim bahwa dataset dilarang digunakan. [Situs resmi](https://sortwaste.di.ubi.pt/), [repo resmi](https://github.com/sarainacio/SortWaste).

**Keputusan yang disarankan:** pertimbangkan setelah ketentuan data jelas, sebagai tambahan untuk benda kusut/tertutup dan kemasan. Kelas material luas memerlukan anotasi ulang jika target kita adalah bentuk benda, misalnya cangkir versus botol HDPE. Perspektif conveyor perlu dilengkapi foto kamera pengguna.

### D.Waste Post-Event: tambahan kecil, bukan fondasi tunggal

Penerbit menyatakan CC BY-SA 4.0 untuk Post-Event Waste: 59 foto acara publik dengan bbox tujuh kategori material/sampah. Distribusinya sangat timpang, termasuk Paper 6 instance dan Biological 2. Dataset Garbage sepuluh kelas pada halaman yang sama merupakan koleksi berbeda; jangan menyimpulkan semua koleksi menyediakan bbox yang sama. [Halaman data penulis](https://www.dwaste.live/dataset/).

**Keputusan yang disarankan:** kandidat audit konteks luar ruangan setelah pemeriksaan provenance/ketentuan berbagi; bukan pilihan pertama untuk menambah kelas mug atau mengejar akurasi kamera rumah tangga.

## Usulan eksperimen berikutnya untuk proyek

Usulan berikut adalah keputusan desain WasteNot, bukan hasil training atau klaim penerbit:

1. Tetapkan sekitar **10–15 kelas bentuk benda** yang berguna di aplikasi: botol, kaleng minuman, kaleng makanan, mug/cangkir, gelas sekali pakai, kertas, kemasan kardus, karton minuman, kantong plastik, pembungkus/sachet, wadah makanan, tutup, serta alat makan. Jumlah akhir mengikuti instance yang lolos audit. Materi PET/HDPE dan bahan cangkir tetap memerlukan konfirmasi; objek visual tidak langsung menentukan Reuse/Recycle/Trash/Reduce.
2. Buat tabel pemetaan kelas lama dan sumber baru sebelum menggabungkan data. Bila empat keluaran lama dipertahankan, tambah kelas baru dengan definisi tidak tumpang tindih. Bila memilih taksonomi bentuk yang lebih umum, buat versi taksonomi baru beserta mapping backend dan evaluasi ulang; jangan mengubah arti ID pada model lama.
3. Gunakan subset Open Images untuk mug/cangkir dan objek rumah tangga, subset TACO yang hak fotonya terverifikasi untuk variasi sampah, serta data botol/kaleng lama untuk menjaga cakupan. Label klasifikasi gambar saja belum menyediakan bbox; jangan membuat bbox seluruh foto secara otomatis.
4. Tambahkan foto sesi kamera sendiri: benda dipegang, meja berantakan, cahaya ruangan, merek lokal, dan latar tanpa target. Anotasi semua objek target yang terlihat. Pisahkan split menurut benda/sesi/sumber, lakukan deduplikasi sebelum split, dan simpan test kamera terpisah.
5. Latih kandidat terpisah. Bandingkan precision/recall dan confusion tiap kelas, false positive pada latar, serta latensi dengan ukuran input, padding, threshold, dan protokol matching yang sama. Penambahan dataset meningkatkan cakupan yang dapat dipelajari, tetapi kualitas baru dinyatakan meningkat setelah evaluasi yang setara.

Urutan yang disarankan: **mug/cangkir + kertas/kardus + kantong/wadah terlebih dahulu**, lalu kelas langka atau khusus setelah tersedia anotasi memadai. Eksperimen ini tidak mengganti checkpoint demo yang aktif sebelum kandidat lolos evaluasi.
