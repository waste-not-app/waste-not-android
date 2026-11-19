# Field akun dan editor: state yang terbaca

26 Oktober 2026. Checkpoint C08/C09/C11 pada WasteTextField, dipakai form akun, jumlah dan catatan editor. [Manifest](text-field-checkpoint.json) mengikat source/kit, APK, kasus, pixel dan screenshot. Penerimaan seluruh screen/navbar/navigasi tetap [dalam pengerjaan](../../tasks/ITERATION_2_DESIGN.md).

## Masalah dan perbaikan

Ketika Masuk menunggu hasil dengan email/password valid terisi, border masih menggunakan opacity disabled Material; ikon visibility juga pudar. Pada kolom kosong nonaktif, placeholder memakai ink alpha 0,38. WasteTextField sebelumnya hanya mengatur warna nilai terisi; warna subbagian lain masih default.

Warna bersama sekarang memakai surface pada field aktif, soft saat nonaktif, outline untuk border nonaktif dan muted opak untuk nilai, placeholder, label/support serta ikon nonaktif. Tombol visibility diberi disabledContentColor eksplisit agar IconButton di dalam field tidak menambahkan alpha lagi. Warna focused/error tetap primary/error dari Material dengan token WasteNot, radius tetap 12 dan tinggi minimal 56 dp. Label di atas maupun label bawaan memakai skema field yang sama.

| Keadaan | Sebelum | Sesudah |
|---|---|---|
| Email terisi, pending terang | [Border pudar](field-before-account-email-light.png) | [Outline terbaca](field-normal-account-email-light.png) |
| Password pending gelap | [Ikon dan border pudar](field-before-account-password-dark.png) | [Muted/outline opak](field-normal-account-password-dark.png) |
| Placeholder nonaktif terang | [Placeholder alpha](field-before-disabled-empty-light.png) | [Muted opak](field-normal-disabled-empty-light.png) |

[Idle terang](field-normal-idle-light.png), [fokus terang](field-normal-focus-light.png), [error terang](field-normal-error-light.png), [error gelap](field-normal-error-dark.png), [password pending 200%](field-phone200-account-password-light.png), dan [field error 600 dp/200%](field-wide200-error-dark.png).

## Sumber styling

DESIGN §2/6 meminta informasi terkunci tetap terbaca, field opak, label di atas, outline dan error tertulis. CSS kit mendeklarasikan `input:disabled` soft/muted dengan opacity 1, `::placeholder` muted/opacity 1, dan border memakai line. Android mempertahankan soft/muted yang sudah dipakai sebelumnya dan memperbaiki subbagian yang masih memakai alpha.

Ada perbedaan cascade di HTML yang dicatat, bukan disembunyikan sebagai pixel identik: selector `input:not([type=checkbox]):not([type=radio])` lebih spesifik daripada `input:disabled`, sehingga render browser field akun nonaktif tetap surface/ink. Nilai computed kedua tema ada di manifest. Border line dan placeholder muted tetap sesuai hasil browser; soft/muted Android mengikuti deklarasi disabled kit dan kontrak komponen yang sudah dipakai. Kit sumber tidak diubah pada checkpoint ini.

## Verifikasi

Build native, lint Debug, 171 debug unit tests dan gate API 24/adjustResize/lima weight font/OFL lulus. **33 eksekusi native / 11 metode unik** lulus pada API 24 normal, ponsel teks 200%, dan 600 dp teks 200%, nol skip. APK aplikasi/penguji identik pada tiga konfigurasi; APK penguji juga identik dengan reproduksi baseline. Smoke empat kasus tidak ditambahkan ke total.

| API 24 | Lulus | Skip | Detik |
|---|---:|---:|---:|
| 360 dp, normal | 11 | 0 | 71,460 |
| 360 dp, teks 200% | 11 | 0 | 72,231 |
| 600 dp, teks 200% | 11 | 0 | 125,170 |

Kontras border aktual terhadap bidang minimal **3,39:1** pada 36 snapshot. Ini memenuhi 3:1 untuk batas kontrol; kontras tersebut tidak dipakai sebagai nilai kontras teks.

- TextFieldDesignTest: dua tema; email/password valid terisi saat pending, masking, visibility nonaktif tanpa callback, placeholder kosong nonaktif, idle/focus/error, perubahan nilai dan pesan error. Border/background diambil dari screenshot; warna nilai/placeholder diperiksa dari TextLayoutResult; glyph muted visibility dihitung pada screenshot.
- FormControlsTest terpilih: parser jumlah, konversi/satuan, serta reveal/mask password dan submit loading.
- EditorDesignNavigationTest: ketiga alur validasi blur, draf/recreate/save pada kedua tema dan storage failure/correction tetap berjalan.
- SessionRootTest terpilih: IME mengirim sekali, error kredensial mempertahankan input tanpa membuka root privat, registrasi/konfirmasi/timezone dan pembersihan root akun tetap benar.

36 snapshot field menghasilkan 72 sampel border/background dan enam pemeriksaan pixel glyph visibility. Kontras border terhadap bidang dihitung dari pixel aktual; nilai minimum dan durasi tiap profil tercatat di manifest. Ini tidak mengukur warna semua kontrol di aplikasi.

Baseline akhir dengan APK penguji yang sama menghasilkan dua metode gagal dan dua metode aktif/focus/error lulus. [Log](text-field-baseline-failure.log) menyimpan kegagalan border/placeholder/glyph. Fixture awal untuk kedua tema masih memiliki fokus pada kolom ketika tema berikutnya diharapkan idle; kasus aktif/focus/error kemudian dipisah per tema pada host uji baru. Hasil awal itu tidak disebut defect produk atau dihitung sebagai lulus. Cakupan metode baseline/akhir identik setelah pemisahan tersebut.

## Batas checkpoint

Hard/Purpose/Craft lulus untuk state dan konfigurasi yang diperiksa: kontrol tetap berfungsi, label/glyph terbaca, informasi pending tidak hilang, error tertulis dan gate tetap. ENERGY 2 / RHYTHM 2 / MOTION 1 tetap dari DESIGN; perubahan ini tidak menambah gerak.

Seluruh screen/navbar/navigasi belum diterima. Picker/selector/chip, visual pressed/focus global, semua transisi route/predictive seek, password manager eksternal, spoken TalkBack, perangkat fisik dan API lebih baru masih terbuka. Fixture memakai dummy; ini tidak membuktikan autentikasi/penyimpanan/inference server.
