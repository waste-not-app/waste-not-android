# A07: Komponen form dan pilihan

Issue [#10](https://github.com/waste-not-app/waste-not-android/issues/10), Siap review client.

## Perubahan

- ActionButton berbagi tinggi minimum 52 dp, state loading/disabled dan variasi destructive. Tinggi dapat tumbuh saat font membesar.
- WasteTextField memakai label, helper/error, keyboard/IME dari pemanggil, password masking dan tombol tampil/sembunyi berikon bernama. Nilai tetap milik state layar; password tidak ditulis ke storage oleh komponen.
- QuantityField memakai QuantityInput A02 untuk perpindahan g/kg/benda. Perubahan jenis mengosongkan input; tidak mengonversi benda ke gram. Parser yang dipakai submit tetap parseQuantity A02.
- CategoryPicker menampilkan tujuh label lengkap; MethodSelector hanya memilih satu dari empat metode dan menjelaskan artinya. Pilihan kosong tidak diisi diam-diam.
- RangeSelector 7/30 hari dan DateField memakai tanggal kalender UTC untuk protokol picker, lalu mengembalikan LocalDate; batas tanggal diberikan pemanggil berdasarkan hari server/zona akun. Tidak memperlakukan tanggal sebagai timestamp aktivitas.
- Preview terang/gelap dan 200% mencakup empty/error/disabled/loading. Teks aplikasi disimpan sebagai resources dan pilihan dapat wrap.

## Verifikasi

Kompilasi dan build native lulus. Pada PR #94 tiga tes interaksi lulus di API 24: 250 g → 0.25 kg tetap 250 integer gram, perpindahan benda mengosongkan berat dan menolak 2,5 benda lewat parser yang sama; password berganti antara bullet dan teks layout asli; loading menonaktifkan submit; metode awal kosong dan setiap ketukan hanya memilih satu metode. Suite perangkat gabungan A06/A07/Keystore/container berisi 11 tes dan lulus dalam 13,616 detik.

Komponen sekarang dipakai oleh Login/Register, filter/range, dan satu LogEditorScreen untuk create/edit. QuantityField menerima inputModifier agar fokus error menuju text field yang benar, bukan chip satuan. Alur perangkat memeriksa perpindahan 0,25 kg ke benda, nilai yang dikosongkan, simpan/edit, kalender buka/batal, serta error login di atas keyboard nyata pada font 200%. Bukti final dan batas uji pembaca layar ada di [laporan QA](ITERATION_1_QA.md). Preview tetap source pendukung, bukan pengganti bukti perangkat.

DateField kini menggulir isi picker pada jendela pendek. Tes keempat memeriksa tanggal di luar batas, Apply/Cancel, pergantian bulan dan pilihan baris terbawah, termasuk landscape/font 200%. Time picker belum dibuat karena activity_date pada kontrak core hanya tanggal; kontrol waktu ditambahkan bersama task pengingat jika capability tersedia.

TalkBack nyata 16.0 pada API 36/font 200% kini diuji: keyboard USB virtual menavigasi fokus ke label email/password, mengaktifkan Masuk, mencapai pesan validasi, lalu kembali ke aksi utama. Tes menjaga layanan spoken feedback tetap aktif; bukti dan gambar ada di laporan QA. Password manager eksternal tetap belum diuji.
