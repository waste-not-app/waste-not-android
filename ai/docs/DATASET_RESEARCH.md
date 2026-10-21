# Riset dataset deteksi objek WasteNot

Tanggal pemeriksaan: **20 Oktober 2026**. Catatan ini memisahkan fakta dari penerbit, pemeriksaan endpoint langsung, dan keputusan eksperimen WasteNot. Hasil training dan inventaris arsip dilaporkan terpisah; jumlah gambar dari halaman katalog tidak dianggap sebagai jumlah sampel unik yang sudah diaudit.

Untuk perluasan kelas di luar botol/kaleng, lihat [riset dataset yang lebih beragam](BROADER_DATASET_RESEARCH.md): TACO, subset Open Images, batas cakupan cangkir/baterai, dan rencana penggabungan label sebelum eksperimen berikutnya.

## Pilihan eksperimen pertama: Drinking Waste Classification v2

Dataset **Arkadiy Serezhkin** berisi aluminium can, glass bottle, PET bottle, dan HDPE milk bottle, dengan foto dan anotasi YOLO di `YOLO_imgs`. Penulis menyatakan pengumpulan serta pelabelan manual untuk proyek UCL dengan kamera telepon, dan mengakui penggunaan sebagian TrashNet. Metadata Kaggle menampilkan versi 2 dan `CC0: Public Domain`. Ini menyediakan target botol/kaleng yang lebih tepat daripada label umum “plastic” atau “metal”. [Halaman penerbit](https://www.kaggle.com/datasets/arkadiyhacks/drinking-waste-classification), [metadata API penerbit](https://www.kaggle.com/api/v1/datasets/view/arkadiyhacks/drinking-waste-classification).

Endpoint yang diperiksa tanpa akun/API key:

- [Unduhan ZIP versi 2](https://www.kaggle.com/api/v1/datasets/download/arkadiyhacks/drinking-waste-classification?datasetVersionNumber=2).
- [Daftar file API](https://www.kaggle.com/api/v1/datasets/list/arkadiyhacks/drinking-waste-classification), menggunakan pagination untuk inventaris lengkap.

GET unduhan menghasilkan HTTP 200, `application/zip`, panjang **1.605.572.649 byte**, dan tanda awal ZIP `PK` pada pemeriksaan ini. HEAD menghasilkan 404; gunakan GET dengan batas ukuran dan verifikasi ZIP. URL redirect bertanda tangan bersifat sementara dan tidak perlu disimpan. Simpan URL awal, versi, SHA-256 arsip, metadata, dan waktu unduh.

### Provenance dan atribusi gabungan

Jangan menyebut seluruh gambar hasil gabungan sebagai murni CC0. Bagian milik Serezhkin mengikuti deklarasi penerbit; bagian TrashNet tetap memiliki provenance hulu. Repo asli TrashNet menyatakan dataset dikumpulkan Gary Thung dan Mindy Yang, menyediakan gambar serta kode, meminta sitasi, dan menghubungkan unduhan ke akun Hugging Face milik Gary Thung. Kartu dataset pada akun tersebut secara eksplisit menyatakan `license: mit`; repo menyertakan MIT dengan hak cipta Gary Thung 2017. Ini lebih kuat daripada menyimpulkan lisensi gambar hanya dari lisensi kode. [Repo asli](https://github.com/garythung/trashnet), [dataset akun penulis](https://huggingface.co/datasets/garythung/trashnet), [kartu dataset mentah](https://huggingface.co/datasets/garythung/trashnet/raw/main/README.md), [MIT notice](https://raw.githubusercontent.com/garythung/trashnet/master/LICENSE).

Keputusan: pertahankan atribusi Serezhkin, Thung, dan Yang; sertakan salinan MIT notice dalam metadata artefak turunan dan jangan menghapus asal TrashNet. Tidak perlu mengunggah ulang gambar ke GitHub. Lisensi bobot dasar/Ultralytics merupakan lapisan terpisah dari lisensi foto.

### Audit yang wajib dilakukan setelah unduh

1. Baca `labels.txt`; jangan menebak urutan ID berdasarkan urutan kelas pada deskripsi. Pisahkan `rawimgs` dari `YOLO_imgs` agar gambar yang sama tidak dihitung dua kali.
2. Periksa semua pasangan gambar/label, decode, geometri bbox, ID, label kosong, dan duplikasi isi. Catat konflik label, jangan diam-diam membetulkannya dengan prediksi model.
3. Kelompokkan duplikat dan near-duplicate sebelum membuat split. Foto bernomor berurutan dapat berasal dari benda/sesi yang sama: nama file saja belum membuktikan identitas fisik. Laporkan keterbatasan jika identitas benda/sesi tidak tersedia.
4. Jangan memakai TrashNet atau dataset gabungan yang memuatnya sebagai “test eksternal independen” untuk model ini. Dataset asal yang sama bisa muncul dengan ukuran/nama berbeda.
5. Lakukan inspeksi visual sampel setiap kelas dan latar. Model bisa mempelajari meja/latar atau satu merek botol. Test publik ini belum membuktikan kualitas kamera laptop, merek lokal Indonesia, ataupun deteksi objek di tangan.

### Batas scope eksperimen

Model khusus empat kelas ini menambah spesialisasi botol/kaleng. Dataset ini tidak menyediakan bukti keberhasilan untuk baterai, kardus, atau cangkir. Pembedaan PET/HDPE merupakan prediksi visual, bukan pengujian bahan; pemilihan material dan kondisi tetap membutuhkan konfirmasi dalam alur rekomendasi WasteNot. Jangan membuat kelas penanganan Reduce/Reuse/Recycle/Trash dari dataset ini: label objek tidak menentukan kebersihan, kelayakan pakai, kontaminasi, atau penerimaan daur ulang setempat.

Integrasi harus membaca taksonomi checkpoint khusus. Memakai ID 0-3 seolah ID COCO akan menghasilkan mapping salah. Pertahankan model COCO sebagai pilihan luas dan bandingkan hasil kandidat secara eksplisit sebelum menjadikannya default.

## Alternatif yang ditelusuri

| Dataset / sumber utama | Kesesuaian | Alasan belum menjadi pilihan pertama |
|---|---|---|
| [TACO, repo penulis](https://github.com/pedropro/TACO) | COCO bbox/polygon; sampah dalam konteks luar ruangan. Repo membedakan anotasi resmi dan unofficial yang belum ditinjau. | Lisensi toolkit MIT tidak otomatis membuktikan hak setiap foto Flickr. Metadata foto smoke yang sudah ada tidak menetapkan lisensi gambar. Hindari memasukkan foto dengan lisensi belum terverifikasi. Kelas langka perlu penggabungan/audit. |
| [Garbage Classification 3 v2, Material Identification](https://universe.roboflow.com/material-identification/garbage-classification-3/dataset/2) | Penerbit menampilkan object detection, CC BY 4.0, 10.464 gambar, train/val/test, enam kelas material. | Label material luas tidak menjamin “glass bottle” atau “aluminium can”. Halaman menyebut preprocessing stretch dan augmentasi; perlu grouping sumber serta penelusuran provenance gambar sebelum digabung. Ekspor tanpa akun belum diverifikasi. |
| [ZeroWaste, repo penulis](https://github.com/dbash/zerowaste), [rilis data](https://doi.org/10.5281/zenodo.4899926) | Segmentasi sampah di fasilitas sorting; cocok riset clutter/material. | Penerbit menetapkan CC BY-NC 4.0. Domain conveyor berbeda dari demo pengguna; bukan pilihan awal untuk botol/cangkir dan cakupan produk umum. |
| [SortWaste, situs penulis](https://sortwaste.di.ubi.pt/), [repo resmi](https://github.com/sarainacio/SortWaste) | 5.261 gambar, delapan kelas, bbox, format YOLO/COCO; PET, HDPE, kardus, metal, dan lainnya. | Domain conveyor padat. Situs/repo yang diperiksa menyediakan akses tetapi tidak memperlihatkan lisensi data eksplisit; ketersediaan publik saja belum cukup untuk memilihnya. |
| [RealWaste, UCI](https://archive.ics.uci.edu/dataset/908/realwaste) | Lingkungan landfill, sembilan jenis material. | Tugas penerbit adalah klasifikasi gambar; tidak langsung menjadi supervisi bbox. Jangan mengubah label gambar menjadi bbox seluruh foto tanpa anotasi yang sah. |
| [D.Waste, halaman penulis](https://www.dwaste.live/dataset/) | Post-Event Waste: 59 foto dengan bbox tujuh kelas; CC BY-SA 4.0 menurut penerbit. | Kecil, sangat timpang, domain satu acara; lebih masuk akal untuk audit tambahan dengan ketentuan lisensi dan grouping acara daripada satu-satunya sumber training. Dataset Garbage sepuluh kelas pada halaman sama merupakan kumpulan berbeda. |

## Kriteria menerima hasil training

Gunakan split tetap dan catat daftar file/hash. Pilih checkpoint serta threshold pada validation; test akhir dipakai setelah keputusan tersebut. Laporkan precision, recall, mAP50 dan mAP50:95 per kelas, jumlah gambar/instance, confusion matrix, latency, dan kesalahan pada sampel yang diperiksa. Perbandingan dengan COCO memerlukan mapping yang setara: COCO memiliki bottle tetapi tidak empat kelas material ini, sehingga mAP empat kelas COCO tidak boleh dianggap baseline yang sebanding tanpa protokol pemetaan.

Jangan menyatakan peningkatan hanya dari confidence satu foto atau loss training yang turun. Rekam juga objek di luar kelas target dan negatif yang relevan, terutama orang/tangan, cangkir, dan silinder lain, untuk mengecek salah deteksi. Pembuktian demo lokal memerlukan test kamera terpisah yang tidak masuk training.
