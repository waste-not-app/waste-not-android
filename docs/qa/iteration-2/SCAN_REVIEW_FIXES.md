# Review scan: versi panduan dan pilihan manual setelah expiry

Dua reviewer independen meninjau PR102 dari e01a08a ke1c0274c. Review sumber memeriksa standar repo/heuristik kode dan kesesuaian SCREEN_SPEC/AI_SPEC secara terpisah. Sesudah perbaikan, keduanya membaca ulang patch dan tidak menemukan regresi konkret dari koreksi ini. Pemeriksaan ini tidak menyatakan seluruh aplikasi selesai.

## Standards

Tidak ada temuan standar/correctness yang perlu ditindaklanjuti pada diff awal. Jalur generasi/cancellation, validasi transport, autentikasi, provenance/draf/save dan pemanggilan publikasi ditinjau. Reviewer tidak menjalankan Gradle, emulator atau backend; hasil runtime di bawah dijalankan terpisah.

## Spec

Dua temuan P2 diperbaiki:

- SCREEN_SPEC S24 meminta “alasan, sumber/versi panduan”. Dasar saran kini menampilkan ID dan versi panduan yang benar-benar dikirim server melalui guide_refs, bersama langkah dan sumber. Tidak membuat judul, versi atau tautan panduan rekaan.
- AI_SPEC bagian7 meminta kategori/metode yang telah dikonfirmasi tetap dapat disimpan sebagai manual setelah expiry dengan metadata AI dilepas secara eksplisit. Pilihan pengguna kini disimpan terpisah dari saran. Memilih metode atau menekan Gunakan pilihan mengonfirmasinya; expiry tidak menghilangkannya. Input manual membawa kategori/metode tanpa provenance AI. Mengubah objek/kondisi membatalkan konfirmasi lama. Pesan expiry menjelaskan sesi serta tindakan manual tersebut.

## Bukti

24 unit tests ScanViewModel lulus. Kasus tambahan memeriksa tiga expiry (foto/deteksi/rekomendasi), masing-masing dengan penerimaan metode awal atau koreksi, penolakan handoff AI, preservasi pilihan manual, pelepasan provenance, cleanup deteksi dan reset setelah koreksi konteks/objek. [Ringkasan suite](scan-review-fixes/unit-result.json).

Dua metode UI native pada API37, 390×844dp, teks100% lulus dalam37,728detik: membuka dasar saran/versi panduan lalu mengubah metode, serta pesan expiry/input manual pada kedua tema. Delapan PNG adalah frame display dan panel dari empat keadaan. [Hasil](scan-review-fixes/result.json), [log](scan-review-fixes/native.log), [APK dan hash sumber](scan-review-fixes/build.json), [hash gambar](scan-review-fixes/captures.json).

| Keadaan | Terang | Gelap |
|---|---|---|
| Dasar saran dengan versi panduan | [PNG](scan-review-fixes/guide-revision-light-panel.png) | [PNG](scan-review-fixes/guide-revision-dark-panel.png) |
| Expiry dengan pilihan manual dipertahankan | [PNG](scan-review-fixes/expired-choice-light-panel.png) | [PNG](scan-review-fixes/expired-choice-dark-panel.png) |

Gambar memakai fixture komponen, bukan hasil inference produksi. UI perjalanan remote memakai screenshot display langsung: PixelCopy panel dapat gagal setelah Activity berganti karena tema; capture panel tetap dipakai pada preview komponen yang stabil. Paket/window aplikasi dan heading layar tetap diperiksa pada perjalanan nyata.

Pengujian kamera remote terpisah telah membuktikan registrasi/katalog hidup serta foto bertahan saat berganti tema. Deteksi foto uji yang terpotong menghasilkan MANUAL_REQUIRED tanpa objek; itu tidak dihitung sebagai alur saran positif lulus. Akun uji dan seluruh turunannya dibersihkan. Pembingkaian input kamera uji diperbaiki sebelum mengulang jalur positif; threshold dan hasil server tidak diubah agar tes lulus.
