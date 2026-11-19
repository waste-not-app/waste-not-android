# Fokus keyboard pada tombol dan navigasi

7 November 2026, API37, 390×844dp, font100%. Review melanjutkan checkpoint UI pada `3db2eb1`. DESIGN §6/9 meminta fokus terlihat; `design-system.css` menetapkan outline primary 3px dengan offset4px. Pemetaan native memakai dp, sesuai ketentuan kit.

## Temuan dan koreksi

Pada APK sebelum perubahan, fokus keyboard memang berpindah dan callback bekerja, tetapi penandanya hanya state layer/ripple Material. Frame sebelum menunjukkan perubahan warna tipis tanpa outline kit. Primary, secondary, text action, icon action, Catat/Scan dan tab kini memiliki outline primary3dp. Tombol memakai offset4dp dan halo tipis dari surface agar tetap terpisah dari bidang pine/lime. Tab memakai border ke dalam karena setiap sel bersebelahan dalam dock; tidak memperlebar sel atau mengubah pemilihan tab.

Shared modifier hanya menggambar saat fokus. Secondary yang fokus juga memakai primaryContainer/onPrimaryContainer dan outline primary, mengikuti DESIGN §6. Ripple, pressed feedback, ukuran, label, callback serta guard loading/disabled dipertahankan. Tidak menambah dependency, mengubah backend, timeout atau provenance AI.

## Bukti native dan visual

- Sebelum: dua metode `KeyboardFocusDesignTest` lulus dalam28,301detik. Ini membuktikan alur keyboard sebelumnya bekerja, bukan bahwa outline telah tersedia.
- Sesudah: dua metode yang sama serta regresi `ActionButtonDesignTest.touchAndKeyboardRespectLoadingAndEnabledState` dan `SecondaryActionButtonDesignTest.touchAndKeyboardUseTheSameEnabledGateAndKeepTheCallerWidth` lulus dalam84,340detik, nol skip.
- Tab hanya memindahkan fokus; Enter baru memilih Riwayat. Membuka scan dari tombol Catat/Scan lalu mengaktifkan Tutup dengan keyboard mengembalikan fokus ke pemicu dan mempertahankan tab asal, pada kedua tema.
- Regresi memeriksa tombol disabled tidak menjalankan callback, loading menolak touch/Enter berulang, dan tombol kembali bekerja setelah loading selesai.
- [14 frame sebelum](keyboard-focus/before/captures.json) dan [14 frame sesudah](keyboard-focus/after/captures.json), masing-masing dengan bounds fokus. APK penguji identik sebelum/sesudah; APK aplikasi berbeda. [Manifest sebelum](keyboard-focus/before/result.json), [sesudah](keyboard-focus/after/result.json), [log native](keyboard-focus/after/native.log), [patch sumber](keyboard-focus/source.patch) dan [modifier tambahan](keyboard-focus/FocusOutline.kt) mengikat bukti sebelum commit.
- [Pemeriksaan piksel](keyboard-focus/focus-pixel-result.json) menemukan warna outline sesuai token pada14/14 titik, dengan bounds yang sama dan kontras terhadap bidang bersebelahan minimum **6,61:1**. Ini sampel stroke pada viewport tersebut, bukan audit seluruh piksel/state aplikasi. Perhitungan luminance diuji dengan hitam/putih21:1 dan warna identik1:1.
- Warna bidang/label secondary fokus memakai primaryContainer/onPrimaryContainer, dengan state layer Material tetap aktif. Sampel label terhadap bidang fokus minimal6,04:1 pada dua tema. [40 perbandingan crop kontrol yang tidak fokus](keyboard-focus/control-body-comparison.json) identik sebelum/sesudah; ini tidak mengklaim seluruh PNG identik.

| Fokus | Sebelum terang / gelap | Sesudah terang / gelap |
|---|---|---|
| Primary | [Terang](keyboard-focus/before/action-primary-light.png), [gelap](keyboard-focus/before/action-primary-dark.png) | [Terang](keyboard-focus/after/action-primary-light.png), [gelap](keyboard-focus/after/action-primary-dark.png) |
| Secondary | [Terang](keyboard-focus/before/action-secondary-light.png), [gelap](keyboard-focus/before/action-secondary-dark.png) | [Terang](keyboard-focus/after/action-secondary-light.png), [gelap](keyboard-focus/after/action-secondary-dark.png) |
| Text action | [Terang](keyboard-focus/before/action-text-light.png), [gelap](keyboard-focus/before/action-text-dark.png) | [Terang](keyboard-focus/after/action-text-light.png), [gelap](keyboard-focus/after/action-text-dark.png) |
| Icon action | [Terang](keyboard-focus/before/action-icon-light.png), [gelap](keyboard-focus/before/action-icon-dark.png) | [Terang](keyboard-focus/after/action-icon-light.png), [gelap](keyboard-focus/after/action-icon-dark.png) |
| Catat/Scan | [Terang](keyboard-focus/before/action-record-light.png), [gelap](keyboard-focus/before/action-record-dark.png) | [Terang](keyboard-focus/after/action-record-light.png), [gelap](keyboard-focus/after/action-record-dark.png) |
| Tab berbeda dari tab terpilih | [Terang](keyboard-focus/before/navigation-light.png), [gelap](keyboard-focus/before/navigation-dark.png) | [Terang](keyboard-focus/after/navigation-light.png), [gelap](keyboard-focus/after/navigation-dark.png) |
| Kembali dari scan | [Terang](keyboard-focus/before/returned-light.png), [gelap](keyboard-focus/before/returned-dark.png) | [Terang](keyboard-focus/after/returned-light.png), [gelap](keyboard-focus/after/returned-dark.png) |

## Batas checkpoint dan gate

Data akun kosong/guide memakai fixture lokal dan composition root test yang menginjeksi adapter dummy secara eksplisit. APK aplikasi tetap dibangun dengan dummy false, hot reload mati dan HTTPS Go publik; fixture tidak mengubah adapter produk. Ini bukan pengulangan kamera/inference produksi, yang memiliki [bukti terpisah](AI_LIVE_UI.md). Test Activity bukan pemeriksaan warna system bar aplikasi.

Percobaan awal dengan dialog ANR Pixel Launcher ditolak; percobaan keyboard berikutnya memperlihatkan bahwa key injection Compose saja belum memindahkan Android dari touch mode. Driver kemudian memakai Tab melalui input Android sebelum traversal Compose, mengikuti regresi keyboard yang sudah ada. Baseline dan hasil sesudah di atas hanya memakai driver tersebut, tanpa dialog sistem selama tes. Pada boot pengulangan akhir, dialog System UI yang teramati ditutup sebelum preflight menyatakan jendela sehat dan sebelum tes dimulai. Fixture juga menyediakan Surface/content color dan gradient seperti root aplikasi; frame awal yang tidak menyediakan content color tidak dipakai. Diagnostik percobaan awal tetap tersimpan di luar repo. Display emulator dipulihkan setelah pengujian.

Gate checkpoint: **Hard PASS** untuk target/fungsi yang diuji; **Purpose PASS** outline berasal dari kit dan halo menjaga kontras; **Liveliness PASS** ENERGY2/RHYTHM2/MOTION1 dipertahankan tanpa gerak baru; **Craft PASS** pada dua tema dan bounds/sampel yang dicatat. Font besar, seluruh input/checkbox/dropdown, TalkBack, fokus awal judul scan dan semua screen/state tetap membutuhkan penerimaan sesuai scope masing-masing. Gate ini tidak berarti seluruh UI selesai. CI dan publikasi dilaporkan terpisah pada commit yang dipush.
