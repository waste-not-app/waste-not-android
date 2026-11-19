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

Pada API 36/debug normal/font 200%, gestur Back dari tepi layar dengan navigation_mode=2 memunculkan D03 untuk draf dirty. Lanjut mengisi mempertahankan isian dan editor. Tes ini melengkapi app bar/dispatcher; tidak menilai animasi predictive per frame. Lima kasus runtime debug biasa juga lulus pada API 36, terpisah dari suite native API 24.


## Checkpoint Iteration 2: transisi editor

Branch `codex/android-iteration-2-design`, melanjutkan `fac827c`. Catatan Iteration 1 di atas adalah riwayat implementasi; sumber default sekarang dummy persisten per akun dan pintu masuk demo telah dihapus. Status akhir A09 tetap dalam pengerjaan.

Draf dipindahkan ke lifetime entry Navigation 3 agar form yang sedang keluar tidak diganti fallback gagal memuat setelah simpan berhasil. Parent mengakses draf entry aktif untuk kebijakan Back dan hasil save; disposal tidak boleh membersihkan draf request lain. Form yang sudah berhasil disimpan tetap nonaktif selama keluar.

[Bukti native dan screenshot](../qa/iteration-2/NAVIGATION_MOTION.md) mencakup lima alur transisi, 40 eksekusi dari 14 kasus unik pada API 24/36, ponsel/rail dan teks normal/200%, tanpa skip. Regresi meliputi failure STORAGE/Activity recreate/koreksi, filter dan gestur Android 36: Back pertama menutup keyboard, berikutnya membuka D03, Batal mempertahankan isian. Probe awal dengan APK tes identik berubah dari 14 sampel fallback error menjadi nol. Durasi fade tetap 180/120 ms.

Ini penerimaan checkpoint yang diuji, bukan audit motion seluruh aplikasi atau predictive seek per frame. Layar dan komponen lain mengikuti [matriks Iteration 2](ITERATION_2_DESIGN.md).

## Checkpoint Panduan dan viewport entry

S11/S12 memakai ID artikel primitive dan optional guideId pada editor. GuideViewModel berada pada lifetime akun; query/kategori tidak ikut pulih ke epoch akun lain. Draf yang sudah dimulai tidak ditimpa saat artikel dimuat ulang.

Scaffold/dock/rail sekarang dimiliki setiap entry agar daftar yang sedang keluar tidak membesar lalu membatasi scroll ketika dock detail disembunyikan. Callback app bar membaca stack dan handler Back terbaru, sehingga D03 dan filter tetap berfungsi. Aksi Cari dan pemilihan artikel memindahkan fokus ke kontrol yang dipilih agar field tidak membuka IME dan menarik scroll kembali. [Bukti per frame dan perjalanan ponsel/rail](../qa/iteration-2/GUIDE.md) mencatat 45 eksekusi pada APK final, satu skip, serta batas audit API 36/keyboard/spoken feedback yang masih terbuka.
