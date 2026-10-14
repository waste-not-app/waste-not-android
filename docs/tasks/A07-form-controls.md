# A07: Komponen form dan pilihan

Issue [#10](https://github.com/waste-not-app/waste-not-android/issues/10), In progress.

## Perubahan

- ActionButton berbagi tinggi minimum 52 dp, state loading/disabled dan variasi destructive. Tinggi dapat tumbuh saat font membesar.
- WasteTextField memakai label, helper/error, keyboard/IME dari pemanggil, password masking dan tombol tampil/sembunyi berikon bernama. Nilai tetap milik state layar; password tidak ditulis ke storage oleh komponen.
- QuantityField memakai QuantityInput A02 untuk perpindahan g/kg/benda. Perubahan jenis mengosongkan input; tidak mengonversi benda ke gram. Parser yang dipakai submit tetap parseQuantity A02.
- CategoryPicker menampilkan tujuh label lengkap; MethodSelector hanya memilih satu dari empat metode dan menjelaskan artinya. Pilihan kosong tidak diisi diam-diam.
- RangeSelector 7/30 hari dan DateField memakai tanggal kalender UTC untuk protokol picker, lalu mengembalikan LocalDate; batas tanggal diberikan pemanggil berdasarkan hari server/zona akun. Tidak memperlakukan tanggal sebagai timestamp aktivitas.
- Preview terang/gelap dan 200% mencakup empty/error/disabled/loading. Teks aplikasi disimpan sebagai resources dan pilihan dapat wrap.

## Verifikasi

Kompilasi dan build native lulus. Tiga tes interaksi lulus di API 24: 250 g → 0.25 kg tetap 250 integer gram, perpindahan benda mengosongkan berat dan menolak 2,5 benda lewat parser yang sama; password berganti antara bullet dan teks layout asli; loading menonaktifkan submit; metode awal kosong dan setiap ketukan hanya memilih satu metode. Suite perangkat gabungan A06/A07/Keystore/container berisi 11 tes dan lulus dalam 13,616 detik.

Keyboard/IME pada layar final, fokus error, seluruh label kategori/picker, TalkBack dan wiring create/edit masih dikerjakan. Preview source belum dianggap bukti rendering perangkat. Komponen ini belum menutup seluruh kriteria issue.

Time picker belum dibuat karena activity_date pada kontrak core hanya tanggal; kontrol waktu ditambahkan bersama task pengingat jika capability tersedia.
