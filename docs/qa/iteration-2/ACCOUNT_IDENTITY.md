# Identitas akun dummy setelah login ulang

23 Oktober 2026. Perbaikan lanjutan S02/S03/S14/S16; bukan penerimaan visual seluruh aplikasi. [Checkpoint](account-checkpoint.json) mengikat APK native, penguji, source dan hasil.

Nama dan zona IANA hasil registrasi kini disimpan per ID akun. Logout menghapus sesi aktif sambil mempertahankan identitas dan namespace catatan masing-masing akun. Login yang dinormalisasi mengembalikan profil yang sama; pendaftaran email yang sudah tersimpan menghasilkan EMAIL_EXISTS. Akun lama dengan field sesi tanpa registry tetap dapat dipulihkan. Password/token tidak disimpan. Ini adapter dummy, bukan autentikasi server; login email yang belum dikenal masih memakai identitas fixture generik.

Profil rusak menghasilkan STORAGE dan tidak diganti identitas sukses. Logout tetap dapat membersihkan sesi aktif yang rusak. Uji commit-false memeriksa pemulihan cache preference serta sesi sebelumnya. Pembatalan sebelum commit login selesai memulihkan sesi sebelumnya; pembatalan bootstrap tidak boleh menerbitkan identitas.

## Bukti

- Regresi identitas gagal pada APK sebelumnya: Wulan/Tokyo berubah menjadi Pengguna/Jakarta. Kasus dan APK penguji yang sama lulus setelah registry ditambahkan.
- Dua kasus pembatalan gagal pada kandidat registry pertama, lalu lulus dengan APK penguji yang sama setelah batas commit diperbaiki.
- Build native, lint dan 137 unit tests lulus (0 gagal/error/skip).
- API 24, 360 dp, teks 100%: 36 kasus runner, **35 dijalankan lulus**, satu kasus gestur khusus versi Android lebih baru dilewati; 116,727 detik. Mencakup delapan kasus identitas, storage/idempotency, registrasi/login melalui UI, tema, navigasi, sesi dan statistik.
- Journey UI berganti antara akun awal lima catatan/50 XP dan akun Wulan kosong/0 XP, lalu login kembali sebagai Wulan tanpa mencampur data. [Profil setelah login ulang](account-returning-profile.png).

Uji rekonstruksi object tidak membuktikan process death sistem. Fault commit dimodelkan oleh penguji; ini bukan pengujian kerusakan disk fisik. Visual auth, navbar dan seluruh layar masih dikerjakan; CI publik diperiksa berdasarkan commit sesudah publikasi.
