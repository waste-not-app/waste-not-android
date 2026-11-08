# A10: Dock lima tujuan dan action Catat

Checkpoint lanjutan: [QA Panduan dan frame navigasi](../qa/iteration-2/GUIDE.md). Klaim ikon/teks hilang pada screenshot sebelumnya dicabut setelah PNG asli dibaca ulang; [koreksi visual](../qa/iteration-2/VISUAL_RECHECK.md). A10 tetap dalam pengerjaan untuk audit seluruh layar dan alur kamera.

Issue [#33](https://github.com/waste-not-app/waste-not-android/issues/33), branch `codex/android-auth-navigation`, Siap review client.

## Implementasi

- Lima ikon vektor berlabel dan semantics Role.Tab. Action lime Catat berada di atas dock dan memakai Role.Button; tidak menjadi tab keenam.
- Dock opak bertambah tinggi ketika label membungkus pada font besar. Target tab minimum 80 dp tinggi; action minimum 52 dp. Scaffold mereservasi ruang seluruh dock; navigationBarsPadding menjaga area sistem.
- Mulai 600 dp, rail 112 dp menggantikan dock, bertambah menjadi 144 dp pada font scale ≥1,5. Action memakai ikon di atas teks agar Catat tidak pecah per huruf. Rail dapat discroll pada jendela pendek/font besar. Form dibatasi 560 dp; konten umum memakai satu kolom terpusat sampai 840 dp.
- Action membuka editor manual dari stack aktif; Back kembali ke tujuan asal. Editor dan auth menyembunyikan dock/rail. Tombol Input manual pada Home/History tetap sekunder.
- Capability scan belum diaktifkan pada increment core ini. Label Catat dan ikon tambah mengarah ke input manual; tidak meminta kamera, membuka simulasi AI, membuat inference atau menambah tab.

## Bukti dan pekerjaan tersisa

Uji perangkat 360 dp/200% memeriksa target ≥48 dp dan action dari kelima tab. WideNavigationTest memeriksa rail vertikal, akses Catat setelah rail digulir, lebar form, serta kembali ke tab asal. QA screenshot menemukan label Catat terpecah per huruf pada rail pendek, lalu susunan ikon/teks dan lebar font besar diperbaiki. Bukti rail/landscape final ada di [laporan QA](ITERATION_1_QA.md). Tidak ada dependency blur atau adaptive eksperimental yang ditambahkan.
