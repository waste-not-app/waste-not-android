# Tombol ikon header dan sheet

26 Oktober 2026. Checkpoint C06 untuk enam tindakan: Kembali dan Tampilan pada akun, Profil pada Beranda, Kembali pada sublayar, Filter pada Riwayat, serta Tutup pada scan. Penerimaan semua screen/navbar/navigasi tetap terbuka pada [matriks](../../tasks/ITERATION_2_DESIGN.md). [Manifest](icon-action-checkpoint.json) mencatat hash source, sumber kit, APK aplikasi/penguji, kasus native, hasil pixel dan screenshot.

## Perubahan yang terlihat

`IconActionButton` memakai bidang soft, glyph ink 24 dp, radius 12 dp dan target 48×48 dp dari `.icon-btn` kit v1.3. Keadaan nonaktif memakai soft/muted opak dan tidak menerima aksi. Gap 8 dp memisahkan Back dan Tampilan pada header Daftar. Label aksesibel, resource ikon lokal dan callback tetap berasal dari masing-masing layar.

| Bagian | Sebelum | Sesudah |
|---|---|---|
| Tampilan akun, terang | [Bidang transparan](icon-before-auth-theme-light.png) | [Bidang soft](icon-normal-auth-theme-light.png) |
| Profil Beranda, terang | [Radius 16 dp dan tint berbeda](icon-before-home-profile-light.png) | [Radius 12 dp dan ink](icon-normal-home-profile-light.png) |

[Back akun gelap](icon-normal-auth-back-dark.png), [Tampilan akun gelap](icon-normal-auth-theme-dark.png), [Profil gelap](icon-normal-home-profile-dark.png), serta [ikon aktif/nonaktif terang](icon-normal-icons-light-0.png) dan [gelap](icon-normal-icons-dark-1.png).

Aksi Catat/Scan C35, visibility password dan penghapus query tetap memiliki konteksnya sendiri. Tidak ada parameter selected yang belum dipakai. Pemeriksaan selected/pressed/focus dan tindakan dalam field belum dinyatakan selesai.

## Bukti pemeriksaan

Build native, lint Debug, 171 debug unit tests dan verifier API 24/adjustResize/lima weight font/OFL lulus. Sembilan metode native dijalankan pada tiga konfigurasi: 360 dp normal, 360 dp teks 200%, dan 600 dp teks 200%; total **27 eksekusi lulus, nol skip**, dengan app dan test APK identik. Smoke enam kasus hanya diagnosis dan tidak ditambahkan ke total.

| API 24 | Lulus | Skip | Detik |
|---|---:|---:|---:|
| 360 dp, normal | 9 | 0 | 61,839 |
| 360 dp, teks 200% | 9 | 0 | 70,798 |
| 600 dp, teks 200% | 9 | 0 | 111,165 |

36 pasangan kontras glyph/background pada gallery aktif/nonaktif lulus, minimum 5,40:1. Pemeriksaan header menambah 54 sampel bidang/sudut dan 18 hitungan pixel glyph pada kedua tema/tiga konfigurasi. Angka ini tidak mencakup warna semua kontrol dalam aplikasi.

- `IconActionButtonDesignTest`: pixel soft pada tiga titik termasuk sudut 12 dp, glyph ink/muted yang benar, kontras minimal 3:1, target sentuh dan ketersediaan semantics pada kedua tema; Tab/Enter serta sentuhan menghormati gate nonaktif.
- `HeaderIconDesignTest`: render Auth dan Beranda nyata dalam fixture deterministik, kedua tema, ukuran/callback Back, Tampilan dan Profil, serta pixel glyph dan sudut. Ini bukan pengganti screenshot seluruh halaman.
- `HeaderIconNavigationTest`: Profil → Pengaturan → Back, Riwayat → Filter → Batal, Catat/Scan → Tutup; tab asal tetap dipilih dan penutupan scan tidak membuka editor.
- `ProfileNavigationTest` dan `Iteration2DesignTest`: tema/persistensi, pembatalan keluar, isolasi akun baru, lima tujuan, manual entry, Back dan recreation tetap berjalan. Label tab tetap utuh dan tidak overflow pada teks besar.

[Beranda/dock terang](icon-normal-home-light.png), [gelap](icon-normal-home-dark.png), [dock teks 200%](icon-phone200-home-light.png), [rail teks 200%](icon-wide200-home-light.png) dan [scan sheet](icon-normal-capture-sheet.png) diambil dari journey aplikasi dengan APK yang sama.

Journey menggulir Beranda agar sapaan terlihat; pada rail/teks besar sebagian tanggal dan profil berada di luar viewport. Gambar ini tidak dipakai untuk menerima keseluruhan header/inset. Gallery dan fixture header memeriksa warna idle; screenshot sheet juga dapat memuat indikasi interaksi Material, sehingga tidak dipakai sebagai sampel token idle.

## Regresi yang benar-benar ditangkap

Baseline sebelum perubahan menghasilkan dua kegagalan native: Tampilan masih transparan, sedangkan Profil memakai radius/tint yang berbeda dari kit. [Log baseline](icon-action-baseline-failure.log) disimpan; hasil baseline tidak dihitung sebagai lulus.

Kandidat awal sesudah perubahan sempat gagal saat pixel tema gelap diambil tepat setelah tombol Profil disentuh dan tema diganti. Pengulangan menghasilkan warna campuran yang berbeda. Test menunggu ripple native API 24 selesai pada waktu nyata; memajukan Compose clock saja belum cukup untuk render thread. APK aplikasi sebelum/sesudah koreksi pengambilan gambar tetap identik. Toleransi pixel dan nilai token tidak dilonggarkan. Hash log diagnosis tersimpan di manifest; hasil gagal tidak dimasukkan dalam total akhir.

## Batas checkpoint

Hard/Purpose/Craft lulus untuk kontrol dan konfigurasi di atas: label nyata, callback teruji, warna dan target mengikuti kit, kedua tema serta teks besar diperiksa. Arah ENERGY 2 / RHYTHM 2 / MOTION 1 tetap mengikuti DESIGN; perubahan ini tidak menambah gerak dekoratif.

Ini belum membuktikan seluruh screen sesuai mockup. Audit pressed/focus visual semua kontrol, C08/C09/selector, transisi semua route/predictive seek, spoken TalkBack, perangkat fisik dan API lebih baru tetap berjalan. Adapter data tetap dummy persisten; integrasi HTTP dan AI nyata belum dibuktikan. PR tetap draft.
