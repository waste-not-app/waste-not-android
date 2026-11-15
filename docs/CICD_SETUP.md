# CI/CD dan distribusi APK WasteNot

## Deployment aktif

Android CI dan `Android Release & Deploy` berjalan pada runner self-hosted
`alberd-staging-android`. Backend, AI dan landing memakai runner masing-masing
di VM `alberd-staging`. Akses operator: `ssh alberd-staging`
(`dev@proxy.bccdev.id`, port `11048`).

- Landing: `https://waste-not.bccdev.id/`
- Halaman rilis: `https://waste-not.bccdev.id/apk/`
- Metadata: `https://waste-not.bccdev.id/apk/versions.json`
- APK terbaru: `https://waste-not.bccdev.id/apk/latest.apk`
- APK versi tertentu: `https://waste-not.bccdev.id/apk/wastenot-v<VERSION>.apk`
- API Android: `https://waste-not-api.bccdev.id/v1/`

Layanan WasteNot lama di `cp-bcc` dan runner lokal bukan target deploy aktif.
Frontend lama di `pg-bcc` mengalihkan pengguna ke domain landing baru.

## Pemicu dan konfigurasi

Push branch utama menjalankan Android CI, bukan publikasi APK. Publikasi dipicu
oleh tag `v*` atau `workflow_dispatch`. Workflow release menjalankan lint/unit
test, membangun APK native, memeriksa adapter remote, lalu menyalin APK signed
langsung ke filesystem VM runner. Tidak ada SSH/SCP dalam langkah publikasi.

Repository variable `WASTENOT_RELEASE_API_BASE_URL` berisi
`https://waste-not-api.bccdev.id/v1/`. URL kosong menghentikan publikasi.
Signing memakai secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS` dan `ANDROID_KEY_PASSWORD`. Deployment menolak APK tanpa
release keystore.

Secret opsional `BACKEND_DIR` mengganti direktori publikasi, default
`/var/www/wastenot-apk`. Nginx harus memakai direktori yang sama.
Secrets SSH deployment lama tidak dipakai workflow release ini.

## Filesystem dan routing Nginx

`scripts/manage_releases.py <BACKEND_DIR>` membaca `<BACKEND_DIR>/apk/`,
mempertahankan tiga APK versi terbaru, menyinkronkan `apk/latest.apk`, dan
membuat `index.html` serta `versions.json` di direktori induk.

Konfigurasi `/etc/nginx/sites-available/waste-not` memetakan:

| URL | File atau direktori |
|---|---|
| `/` | `/var/www/wastenot-landing/` |
| `/apk/` | `/var/www/wastenot-apk/index.html` |
| `/apk/versions.json` | `/var/www/wastenot-apk/versions.json` |
| `/apk/<file>.apk` | `/var/www/wastenot-apk/apk/<file>.apk` |

Metadata memakai `Cache-Control: no-store`. APK memakai MIME
`application/vnd.android.package-archive` dan `Content-Disposition: attachment`.
Subfolder filesystem `apk/` tidak diulang dalam URL publik.

## Verifikasi

`python3 scripts/verify_release_workflow.py` menjalankan langkah build,
packaging, dan deploy workflow dalam direktori sementara. Pemeriksaan deploy
memastikan metadata, tautan halaman rilis, serta file versi/latest cocok.
Fixture pengujian tidak dipublikasikan.

Setelah publikasi, periksa metadata dan unduh file yang ditunjuk metadata:

```sh
curl --fail https://waste-not.bccdev.id/apk/versions.json
curl --fail --head https://waste-not.bccdev.id/apk/latest.apk
curl --fail https://waste-not-api.bccdev.id/health/ready
```

Bandingkan ukuran dan SHA-256 APK hasil unduhan dengan file di VM. Buka landing
di browser: informasi versi harus termuat dan tombol unduh harus mengambil APK,
bukan HTML fallback SPA. Build/HTTP tidak membuktikan alur aplikasi di perangkat;
pemeriksaan perangkat fisik dijalankan terpisah atas permintaan pemilik.
