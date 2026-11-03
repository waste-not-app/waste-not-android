# Kategori dan picker saat form terkunci

3 November 2026. Checkpoint C12/C11/C31, dengan regresi field C08/C09, editor dan filter Riwayat. [Manifest](picker-checkpoint.json) mencatat source/kit, APK, baseline, metode native, pixel dan gambar. Penerimaan seluruh screen/navbar/navigasi tetap [dalam pengerjaan](../../tasks/ITERATION_2_DESIGN.md).

## Perubahan

CategoryPicker sekarang memakai palet field bersama WasteTextField: surface aktif, soft nonaktif, border outline dan placeholder/nilai/panah muted opak. Palet berada pada satu fungsi internal; nilai token tidak berubah. Kategori tetap read-only dengan menu tujuh pilihan dan label aksesibel.

State terbuka pada menu kategori/satuan mengikuti ketersediaan kontrol. Ketika form dinonaktifkan, menu ditutup dan pilihan yang lama tidak diterapkan otomatis. Setelah aktif kembali, menu tetap tertutup sampai tindakan pengguna berikutnya. Item menu juga menerima gate enabled. Kalender memakai input enabled pada state saveable dan guard tampil, sehingga kalender tidak tetap terbuka atau muncul dari state restore ketika kontrol tidak aktif. Tidak ada callback pemilihan pada penutupan karena lock.

| Kategori nonaktif | Sebelum | Sesudah |
|---|---|---|
| Placeholder terang | [Alpha dan border pudar](picker-before-category-empty-light.png) | [Muted/outline opak](picker-normal-category-empty-light.png) |
| Nilai gelap | [Panah dan border pudar](picker-before-category-filled-dark.png) | [Nilai/panah terbaca](picker-normal-category-filled-dark.png) |

[Teks 200%](picker-phone200-category-filled-light.png) dan [600 dp/200%](picker-wide200-category-empty-dark.png). Screenshot ini adalah fixture komponen; tidak dipakai sebagai bukti seluruh halaman editor.

Palet mengikuti DESIGN §2/6 dan deklarasi field pada CSS kit. Perbedaan cascade disabled pada HTML yang ditemukan sebelumnya tetap dicatat di [QA field](TEXT_FIELDS.md); tidak ada klaim pixel identik untuk bagian tersebut. Radius 12, input minimal 56 dp, font dan label tetap dari kit.

## Reproduksi dan regresi

APK baseline `4b22797` menghasilkan **empat metode gagal**: warna/placeholder/border/panah kategori nonaktif dan tiga picker yang tetap ada setelah enabled menjadi false. [Log baseline](picker-baseline-failure.log) memuat node IsPopup/IsDialog yang masih ditemukan. Baseline tidak dihitung sebagai lulus. Source kasus reproduksi dan APK penguji sama sebelum/sesudah; token/toleransi tidak dilonggarkan.

Build native, lint Debug, 171 debug unit tests dan gate API 24/adjustResize/lima weight font/OFL lulus. **54 eksekusi native / 18 metode unik**, nol skip, pada normal, ponsel teks 200%, dan 600 dp teks 200%. APK aplikasi/penguji identik pada ketiga konfigurasi. Smoke empat kasus tidak ditambahkan ke total.

| API 24 | Lulus | Skip | Detik |
|---|---:|---:|---:|
| 360 dp, normal | 18 | 0 | 88,496 |
| 360 dp, teks 200% | 18 | 0 | 79,956 |
| 600 dp, teks 200% | 18 | 0 | 160,611 |

Border terhadap bidang minimal **3,39:1**; muted terhadap bidang minimal **5,40:1**. Kedua tema dan nilai kosong/terisi diperiksa. Font scale aktual tercatat pada snapshot; readback akhir mengonfirmasi layar 1080 px/density 288 (600 dp) dan font 2,0 sebelum pengaturan dipulihkan.

- PickerAvailabilityTest: kedua tema dan kategori kosong/terisi, warna dari TextLayoutResult, border/background/glyph dari screenshot, ukuran minimum, serta ketiga picker ditutup pada lock. Nilai dan jumlah callback tetap; re-enable tidak membuka kembali menu. Pemilihan kategori baru dan konversi g → kg sesudah membuka kembali tetap eksplisit dan terjadi sekali. Kalender dibuka ulang lalu Batal tanpa perubahan tanggal/callback.
- TextFieldDesignTest: empat metode akun pending valid terisi, masking/visibility, placeholder nonaktif, idle/focus/error dan perubahan nilai. Pemindahan palet tidak mengubah kontrak field akun/catatan.
- FormControlsTest: enam metode mencakup jumlah/satuan, masking/loading, single-choice metode, kalender create/batas masa depan serta tanggal edit lama yang dipertahankan.
- EditorDesignNavigationTest: validasi blur, draf/recreate/save kedua tema dan storage failure/correction tetap benar.
- HistoryDesignTest terpilih: filter draft, Batal/reset/Terapkan dan recreation mempertahankan makna masing-masing, termasuk kalender filter yang memakai DateField sama.

Dua belas snapshot kategori menghasilkan 24 sampel border/background dan 12 hitungan glyph panah. Kontras dihitung dari bidang aktual serta warna glyph yang ditemukan; nilai dan durasi tiap profil ada di manifest. Ini bukan pengukuran semua kontrol aplikasi.

## Batas checkpoint

Hard/Purpose/Craft lulus untuk kontrol, state dan konfigurasi di atas: label dan panah terbaca, kontrol terkunci tidak menawarkan picker aktif, nilai tetap dan pemilihan eksplisit tetap bekerja. Palet bersama mencegah field teks dan kategori menyimpang. ENERGY 2 / RHYTHM 2 / MOTION 1 tetap mengikuti DESIGN; tidak ditambah gerak dekoratif.

Semua screen/navbar/navigasi belum diterima. Audit visual selected/error/pressed/focus lintas selector/chip/range, pemulihan proses nyata dengan modal terbuka, spoken TalkBack, perangkat fisik/API lebih baru, serta alur core lain pada matriks tetap berjalan. Kasus lock kalender ini tidak menguji proposal tanggal baru yang belum diterapkan; pemilihan/Terapkan/Batal diperiksa pada regresi kalender terpisah. Data tetap dummy persisten; ini tidak membuktikan backend atau inference server.
