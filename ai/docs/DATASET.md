# Dataset dan evaluasi pilot

Taksonomi usulan untuk fine-tuning berikutnya:

| ID target | Label | Catatan anotasi |
|---|---|---|
| 0 | plastic_bottle | Identitas material dikonfirmasi saat pengumpulan; bukan hanya tebakan visual |
| 1 | glass_bottle | Pisahkan benda/sesi dari train/validation/test |
| 2 | aluminium_can | Tidak mencampur semua silinder/logam |
| 3 | cardboard | Kotak dan potongan kardus dengan definisi anotasi konsisten |
| 4 | battery | Cakupan tipe baterai harus ditetapkan; deteksi gagal bukan bukti benda aman |

ID ini **bukan** ID COCO. Jangan menggunakan `yolo11n.pt` pretrained untuk menghitung mAP terhadap label 0–4 ini seolah mapping-nya sama. Fine-tuning baru dijalankan setelah dataset dan anotasi tersedia.

Untuk setiap gambar, catat `image_id`, SHA-256, source URL/asal pengambilan, author/consent, license, physical_object_id, capture_session_id, split, kategori, dan bbox. Audit lisensi foto, anotasi, kode dan bobot secara terpisah. Foto pengguna atau koreksi pengguna tidak otomatis menjadi data training. Sampel ilustrasi smoke hanya memeriksa adapter, bukan dataset pilot.

Pisahkan train/validation/test berdasarkan objek fisik dan sesi; jangan memecah frame hampir sama ke split berbeda. Audit duplicate hash, kemiripan gambar, ketimpangan kelas, kebocoran latar, dan kualitas anotasi. Rasio/jumlah gambar ditetapkan setelah inventaris, bukan dianggap sudah cukup dari sejumlah foto demo. Sediakan gambar tanpa objek target untuk unknown rejection.

Matriks pengambilan meliputi cahaya terang/redup, background rumit, objek kecil/tertutup, banyak objek, material mirip, perangkat dan lokasi berbeda. Panduan anotasi harus mendefinisikan occlusion, truncated objects dan bbox; materi ambigu diberi review, bukan label pasti yang direkayasa.

Laporan akhir: jumlah gambar/instance tiap split dan kelas, precision/recall per kelas, mAP@0.5/mAP@0.5:0.95, confusion matrix, false negative special waste, unknown rejection, calibration dan ukuran sampel. Pilih threshold pada validation set; test set hanya untuk hasil final/regresi. Acceptance pengguna tidak menjadi ground truth akurasi.

Evaluasi LLM terpisah: objek/kondisi × panduan/versi × expected allowed method/status/evidence. Sertakan UNKNOWN, tanpa panduan, expired, special handling, keempat label valid REDUCE/REUSE/RECYCLE/TRASH, scope PREVENTION/HANDLING, akses daur ulang UNKNOWN/NO, output COMPOST, dua metode sekaligus/field alternatif, evidence palsu, sumber salah, duplikat, timeout, truncation dan injection dalam teks panduan. Fixture transport menguji integrasi HTTP; panggilan Gemini nyata diperlukan untuk menilai kualitas model. Kontrak mengikuti [pembaruan empat metode](RECOMMENDATION_CONTRACT.md).

Semua ukuran kualitas pilot saat ini **BELUM DIUKUR**. Tidak ada training, tuning threshold atau klaim akurasi yang dibuat oleh prototipe ini.
