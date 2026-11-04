# Integrasi Android dengan backend WasteNot

Pemeriksaan remote: 4 November 2026. Ini pekerjaan lanjutan dari [Iteration 2](ITERATION_2_DESIGN.md), dengan penerimaan visual seluruh screen/navbar/navigasi tetap aktif.

## Sumber yang sudah diperiksa

- Android `origin/main`: `c1258596cb71bc7b95dc9a4f140f55f759815c4e`, termasuk pembaruan AI pada `57ffbd9` dan `c125859`. Digabungkan ke branch desain melalui `98ddeaf`; perubahan UI lokal dipertahankan.
- Backend `main`: [`9a6aefdb76b246e06b20546243f6c1dd69e16ac5`](https://github.com/waste-not-app/waste-not-backend/tree/9a6aefdb76b246e06b20546243f6c1dd69e16ac5). Acuan: `docs/ANDROID.md`, `docs/API.md`, `contracts/core.openapi.json` dan `contracts/ai/public.openapi.json` pada commit tersebut.
- Origin HTTPS yang didokumentasikan: `https://pred-autoscaler.bccdev.id/wastenot/api/v1/`. Pemeriksaan baca-saja pada `/wastenot/api/health/ready` menghasilkan HTTP 200 dan `{"ready":true}`. Metadata tanpa token menghasilkan HTTP 401, sesuai dokumentasi autentikasi. Pemeriksaan ini belum membuktikan write, AI live, atau koneksi perangkat Android.

## Kondisi Android saat pemeriksaan

Adapter dummy tetap default selama rangkaian integrasi belum selesai. Login/register/refresh/me/logout dan GET daftar sudah memiliki interface HTTP. `RemoteActivityStore` detail/write, `RemoteGuideStore` dan `UnconfiguredScanRepository` masih mengembalikan NOT_CONFIGURED. Ringkasan server belum menjadi sumber Beranda/Statistik. Mengganti flag adapter sekarang belum menghasilkan aplikasi remote yang lengkap.

## Urutan penyambungan dan penerimaan

1. **Kontrak core dan catatan.** Tambahkan DTO dan POST/GET detail/PATCH/DELETE ke `WasteNotApi`; petakan envelope, pagination dan error backend. Pertahankan UUID dan payload draf setelah timeout create. Bedakan create 201 dan replay 200; tangani IDEMPOTENCY_CONFLICT, LOG_DELETED, VERSION_CONFLICT dan If-Match delete. Request write tidak direplay otomatis oleh transport.
2. **Sesi dan pemisahan akun.** Verifikasi refresh tunggal, token lama setelah rotasi, hasil refresh tidak diketahui, logout dan pembersihan credential/draf. Nama/zona berasal dari profil server; cursor dan cache terikat akun serta filter.
3. **Data layar.** Gunakan dashboard, statistics, progress dan waste-guide server. Agregat tidak dihitung dari satu halaman daftar berisi 20 item. Panduan kosong/PUBLISHED, loading, empty, refresh error dan retry memakai state nyata tanpa menggantinya dengan fixture sukses. Tambahkan pagination/cursor dan deduplikasi riwayat.
4. **AI melalui Go.** APK hanya memakai kontrak publik; Python/model/key Gemini tetap privat. Ikuti capability deteksi/rekomendasi, confirmed_object, input_source, context_revision, kondisi independen, risiko, READY/NEEDS_CONTEXT/UNAVAILABLE dan saran CONDITIONAL beserta conditions_to_verify. Satu label metode menggantikan kontrak alternatif lama. REDUCE pencegahan tetap melalui manual sampai provenance mendukungnya. Tidak auto-save atau memberi XP dari hasil scan.
5. **Penerimaan perangkat.** Jalankan register/login → catat → replay/timeout → detail/edit/konflik/delete → dashboard/statistik → pergantian akun/logout dengan backend, disertai pemeriksaan tema, teks besar dan navbar. Pisahkan bukti MockWebServer, backend/database dan perangkat. Jangan menyatakan integrasi selesai hanya dari readiness atau tes schema.

Base URL tetap konfigurasi build, bukan literal pada layar. Dokumentasikan konfigurasi yang benar-benar diuji sebelum mengubah adapter default; dummy tetap tersedia untuk pengembangan dan pengujian terisolasi, tanpa pintu masuk mode demo di produk. Backend dan deployment tidak diubah oleh checkpoint UI ini.
