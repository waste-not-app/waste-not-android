# A09: Root sesi/auth dan lima back stack

Issue [#28](https://github.com/waste-not-app/waste-not-android/issues/28), branch `codex/android-auth-navigation`, Siap review client.

## Implementasi

- MainNavigation mengumpulkan state sesi dengan lifecycle. Root tunggal Session/Auth/Account diganti melalui state, tanpa event navigasi yang dapat hilang. Root Account membawa userId, epoch dan penanda demo; tidak membawa User, password, atau token.
- Entry tersimpan tidak dirender sebelum cocok dengan identitas yang telah dipastikan. Checking/ConnectionRequired tidak membuka cache privat. Logout dan login ulang user yang sama menghasilkan lifetime root berbeda.
- Root memakai SaveableStateHolder dan ViewModelStore NavEntry decorators. WorkspaceViewModel berada pada entry akun; saat entry dibuang, pekerjaan dibatalkan dan store/state catatan dibersihkan.
- Lima stack Home/History/Guide/Statistics/Profile memakai decorator saveable terpisah. Reselect hanya memilih stack yang ada. History menyimpan scroll/filter, Statistics menyimpan 7/30 hari. Back pada root tab lain kembali ke stack Home; Back Home diserahkan ke sistem.
- Detail hanya membawa log ID. Editor membawa optional log ID dan client request ID. Create dan edit memakai EditorDraft/QuantityInput/parser yang sama, tanpa konversi benda ke gram. State draf nonrahasia memakai Saver; password tetap di ViewModel auth saja.
- Back app bar dan system/predictive Back memanggil fungsi yang sama. Draf dirty meminta D03; cancel mempertahankan isian. Hasil simpan cocok dengan request ID sebelum editor diganti detail, kemudian diakui agar rotasi tidak menavigasi dua kali.

## Sumber data dan batas

Pengguna mengizinkan dummy untuk ketergantungan backend/AI. DemoAccountSession dan DemoActivityStore terpisah dari adapter sesi API/CoreRepository. Demo tidak menerbitkan/menyimpan token atau mengirim input ke server. Dataset PRD 9.4 diberi label Data contoh dan tanggal 21 September 2026.

RemoteActivityStore sudah membaca list melalui CoreRepository; create/update mengembalikan NOT_CONFIGURED sampai integrasi write pada task log tersedia. UI mempertahankan isian dan tidak mengumumkan simpan berhasil pada failure. Ringkasan live tidak dihitung dari halaman list parsial. Panduan memakai placeholder jelas sampai konten diperiksa. Catatan demo hanya bertahan dalam lifetime sesi; persistensi produksi tetap task backend/log berikutnya.

## Bukti saat ini

Sembilan tes SessionViewModel memeriksa validasi sebelum request, single flight, password in-memory, error email, demo eksplisit, bootstrap/retry, cleanup sesuai hasil, serta respons/cancellation lama tidak mengubah submit/logout baru. Tes perangkat memeriksa lima tab, detail/filter/range, rotasi, dirty Back, create/edit, logout dan privasi identitas. Viewport NavDisplay kini dibatasi oleh Scaffold agar scroll/tap tidak mengenai dock di belakang konten. Hasil final, gambar dan batas pengujian ada di [laporan QA](ITERATION_1_QA.md).

Starter Hello Android, repository sample, beranda angka/emoji tanpa provenance dan tes yang hanya memeriksa Loading/greeting dihapus karena tidak dipakai alur baru. Tes penggantinya memeriksa perilaku sesi, draf, dataset dan navigasi aplikasi.
