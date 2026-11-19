# Regresi kamera, AI dan catatan pada main

9 November 2026, source main `24a578829969b36d0d8ce0901dff17882e271471`, sesudah merge #109. Satu metode `RemoteAiNavigationTest.cameraAdviceSaveEditDeleteAndLogoutUseRealBackend` lulus pada API37, 390×844 dp/font100%, memakai APK debug native dengan adapter remote dan Go HTTPS publik. Source produk sama dengan APK yang diterima pada [perbaikan urutan scan](SCAN_CHOICE_ORDER.md); tidak ada perubahan produk baru dalam checkpoint ini.

## Hasil perjalanan nyata

1. Daftar melalui UI dengan akun baru, lalu baca katalog backend.
2. Aktifkan CameraX, ambil JPEG 64.866 byte, dan pertahankan foto saat berganti tema/recreate Activity.
3. Upload foto ke layanan AI: `bottle`, skor `0.8841050267219543`, band **UNCERTAIN** dari server, model `broad-coco-b0f633193403`. Aplikasi mempertahankan band tersebut dan tetap meminta material. Key saran yang diterima: PET, HDPE, kaca. Skor bukan ukuran akurasi dan tidak menentukan material.
4. Pilih material/jenis objek dan kondisi secara eksplisit. Respons pertama NEEDS_CONTEXT; sesudah melengkapi riwayat isi, respons READY/RECYCLE dari `gemini`, `prototype_only=false`. Belum ada catatan/XP pada kedua tahap saran.
5. Editor menerima pilihan yang dikonfirmasi dengan jumlah masih kosong. Isi 250 g dan simpan: tepat satu catatan dan 10 XP. Snapshot server menyimpan penerimaan/provenance AI.
6. Buka detail, edit catatan yang sama tanpa tambahan XP, hapus, lalu baca ringkasan/profil kembali nol. Logout menghapus credential perangkat.

[Log native](ai-live/main-20261109/native.log), [tujuh langkah dan pemeriksaan window](ai-live/main-20261109/native-fixture.json), [ringkasan respons server](ai-live/main-20261109/server-result-summary.json), [provenance yang diterima](ai-live/main-20261109/provenance-result.json), [hasil akhir](ai-live/main-20261109/native-result.json), [binding APK/sumber](ai-live/main-20261109/build-manifest.json). Pengujian memakai akun dan respons layanan nyata; jawaban kondisi benda tetap fixture yang dimasukkan pengguna pengujian.

## Frame yang ditinjau

Lima belas PNG asli diperiksa hash dan waktu berkasnya terhadap awal run, lalu ditinjau. Posisi gulir dapat berbeda antara tema: gambar adalah viewport yang diamati, bukan bukti semua bagian halaman sudah terlihat bersamaan.

| Tahap | Terang | Gelap |
|---|---|---|
| Kamera aktif dan tinjau foto | [Kamera](ai-live/main-20261109/captures/S21-camera-light.png), [foto](ai-live/main-20261109/captures/S21-photo-light.png) | [Foto](ai-live/main-20261109/captures/S21-photo-dark.png) |
| Objek dan konfirmasi material | [S22](ai-live/main-20261109/captures/S22-object-light.png) | [S22](ai-live/main-20261109/captures/S22-object-dark.png) |
| Kondisi | [S23](ai-live/main-20261109/captures/S23-context-light.png) | [S23](ai-live/main-20261109/captures/S23-context-dark.png) |
| Saran READY | [S24](ai-live/main-20261109/captures/S24-recommendation-light.png) | [S24](ai-live/main-20261109/captures/S24-recommendation-dark.png) |

[Perlu konteks](ai-live/main-20261109/captures/S24-needs-context-light.png), [editor sesudah jumlah diisi](ai-live/main-20261109/captures/S05-ai-prefill-light.png), [simpan](ai-live/main-20261109/captures/S06-created-light.png), [detail](ai-live/main-20261109/captures/S08-created-light.png), [profil sesudah hapus](ai-live/main-20261109/captures/S14-after-delete-light.png). Struktur sheet, tahap, warna, tipografi, kartu saran dan dock mengikuti kit yang sudah ditinjau; tidak ditemukan koreksi visual baru pada viewport ini. Foto emulator berasal dari aset CC0 dengan padding; [manifest input](ai-live/main-20261109/camera-input.json). Ini bukan kamera fisik atau benchmark akurasi model.

## Pemulihan driver dan cleanup

Sebelum instrumentation, System UI emulator sempat ANR; dialog milik emulator dipulihkan dan launcher nyata diperiksa. Kesalahan pengiriman parameter driver juga diperbaiki sebelum metode native berjalan; akun percobaan awal tidak tercipta dan diverifikasi nol. Gangguan ini tidak dilaporkan sebagai kegagalan produk atau metode native yang lulus.

Metode native kemudian lulus dalam 107,231 detik. Wrapper awal salah menghitung refresh token melalui `user_id`; schema sebenarnya menghubungkannya melalui `session_id`. Setelah schema diperiksa, ID sesi milik akun ditangkap, akun dihapus dengan guard UUID/email/nama tepat, dan keenam hitungan diverifikasi nol, termasuk token lewat ID sesi yang sudah ditangkap. [Hasil sebelum pemulihan cleanup](ai-live/main-20261109/native-result-before-cleanup-recovery.json), [pemulihan dan nol akhir](ai-live/main-20261109/cleanup-recovery.json). Gambar diambil dari direktori external-files yang benar; tidak dipakai capture lama dari run lain. Ukuran/density/font/night dipulihkan dan emulator task dihentikan.

## CI dan APK publik

[CI PR #109](https://github.com/waste-not-app/waste-not-android/actions/runs/42581934012), [CI main](https://github.com/waste-not-app/waste-not-android/actions/runs/42614471022) dan [rilis main](https://github.com/waste-not-app/waste-not-android/actions/runs/42614471097) lulus pada head masing-masing; [metadata hasil](ai-live/main-20261109/ci-result.json). Log rinci PR menunjukkan kedua unggahan artefak opsional ditolak kuota storage, walaupun status langkah API terlihat sukses. Listing artefak kosong. Ini dibedakan dari gate lint/test/build/policy yang lulus dan deployment APK publik yang berhasil.

[APK produksi b11](https://oktaavsm.bccdev.id/wastenot/apk/wastenot-v1.0.0-b11.apk) berhasil diunduh: 36.408.119 byte, SHA-256 `3ac6eaf6cbac35cfb666437cb6f9a999cd3e79a7d406aa89d495ab617fafe987`. Signature produksi sama dengan rilis sebelumnya, minimum API30, lima font/OFL dan adapter remote lolos gate; [hasil unduh/verifikasi](ai-live/main-20261109/public-release-result.json). Runtime journey di atas memakai APK debug dengan source produk yang sama, bukan instrumentation terhadap APK produksi.

Self-review memeriksa binding sumber/APK, respons server, isolasi akun, cleanup, waktu capture dan batas klaim. Checkpoint ini membuktikan perjalanan positif saat ini; seluruh state gangguan jaringan/expiry/konflik, aksesibilitas lisan, font besar lintas layar serta penerimaan keseluruhan tetap mengikuti scope masing-masing. Goal penuh belum dinyatakan selesai. Penyimpanan aktivitas masih online-first tanpa Room; pengujian ini tidak menambah dukungan offline.
