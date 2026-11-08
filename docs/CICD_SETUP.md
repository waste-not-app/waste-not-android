# Panduan Setup CI/CD & Distribusi APK WasteNot

Dokumen ini berisi panduan lengkap implementasi CI/CD untuk aplikasi Android WasteNot, konfigurasi server distribusi pada domain `oktaavsm.bccdev.id`, serta langkah-langkah manual penyiapan **GitHub Repository Secrets**.

Workflow publikasi memakai adapter remote dan build native (`wastenot.hotReload=false`). Repository variable `WASTENOT_RELEASE_API_BASE_URL` wajib berisi URL HTTPS API, saat ini `https://pred-autoscaler.bccdev.id/wastenot/api/v1/`. Nilai kosong menghentikan build publikasi; secret signing/deployment tetap dikelola terpisah. Pemeriksaan shell memastikan mode native dikirim dan URL kosong tidak menjalankan Gradle.

---

## 1. Arsitektur & Alur Kerja

```text
[ Developer / Git Push ]
        │
        ▼ (push tag v* / push main / workflow_dispatch)
[ GitHub Actions Runner ]
        ├── 1. Setup JDK 17 & Gradle
        ├── 2. Decode Release Keystore dari Secret
        ├── 3. Jalankan lintRelease dan testDebugUnitTest
        ├── 4. Build Signed Release APK native dengan adapter remote
        ├── 5. Simpan Artifact di GitHub Actions
        │
        ▼ (SSH / SCP via port 11023 ke proxy.bccdev.id)
[ VM oktaavsm.bccdev.id ]
        ├── Folder: /var/www/wastenot/apk/
        ├── Salin wastenot-v{VERSION}.apk & sync latest.apk
        ├── Jalankan /var/www/wastenot/manage_releases.py
        │     ├── Retensi: Simpan MAKSIMAL 3 versi (latest + 2 sebelumnya)
        │     ├── Hapus otomatis build terlama jika > 3 versi
        │     └── Generate landing page (index.html) & metadata (versions.json)
        │
        ▼ (Diakses Pengguna)
┌───────────────────────────────────────────────────────────────┐
│ • Landing Page  : https://oktaavsm.bccdev.id/wastenot/       │
│ • Direct Download: https://oktaavsm.bccdev.id/wastenot/download│
│ • Versi Spesifik: https://oktaavsm.bccdev.id/wastenot/apk/... │
└───────────────────────────────────────────────────────────────┘
```

---

## 2. Status Konfigurasi di VM (`okta-pg` / `pg-bcc`)

Semua konfigurasi di server telah selesai disiapkan dan diuji:

1. **Direktori Web**:
   - `/var/www/wastenot/` dan `/var/www/wastenot/apk/` telah dibuat dengan kepemilikan user `dev:dev`.
2. **Nginx Reverse Proxy**:
   - Konfigurasi pada `/etc/nginx/sites-available/oktaavsm` telah ditambahkan blok routing `/wastenot/`, `/wastenot/download` (force download header), dan `/wastenot/apk/`.
   - Nginx telah di-reload dan diverifikasi beroperasi normal (HTTP/2 200).
3. **Script Retensi Otomatis**:
   - `/var/www/wastenot/manage_releases.py` terpasang di VM dan di repositori (`scripts/manage_releases.py`).
   - Logika pembersihan otomatis membatasi jumlah APK versi maksimal 3 buah.
4. **SSH Deploy Key**:
   - Public key dedicated telah ditambahkan ke `/home/dev/.ssh/authorized_keys`.
   - Akses via `ssh -i ~/.ssh/wastenot_deploy_key -p 11023 dev@proxy.bccdev.id` telah diverifikasi sukses.

---

## 3. Langkah Manual: Setup GitHub Secrets

Buka repositori di browser:
👉 **[GitHub Repo Settings > Secrets and variables > Actions](https://github.com/waste-not-app/waste-not-android/settings/secrets/actions)**

Klik tombol **"New repository secret"** dan tambahkan secret berikut:

### A. Kredensial SSH Deployment ke VM

| Name | Value | Keterangan |
|---|---|---|
| `BACKEND_SSH_KEY` | *(Salin isi file private key di bawah)* | Kunci privat untuk deploy ke VM |
| `BACKEND_HOST` | `proxy.bccdev.id` | *(Opsional, sudah ada default di workflow)* |
| `BACKEND_PORT` | `11023` | *(Opsional, sudah ada default di workflow)* |
| `BACKEND_USER` | `dev` | *(Opsional, sudah ada default di workflow)* |
| `BACKEND_DIR` | `/var/www/wastenot` | *(Opsional, sudah ada default di workflow)* |

> **Cara mengambil `BACKEND_SSH_KEY`:**
> Jalankan perintah berikut di terminal lokal untuk melihat atau meng-copy kunci privat:
> ```bash
> cat ~/.ssh/wastenot_deploy_key
> ```
> *Copy seluruh teks dari `-----BEGIN OPENSSH PRIVATE KEY-----` sampai `-----END OPENSSH PRIVATE KEY-----`.*

---

### B. Kredensial Signing APK (Release Keystore)

Signing memakai keystore milik tim yang disimpan di luar Git. Masukkan secret berikut agar release APK ditandatangani. Dokumentasi tidak menyimpan nilai password atau key; lokasi `keystore/wastenot-release.jks` adalah contoh lokal yang diabaikan Git.

| Name | Value | Keterangan |
|---|---|---|
| `ANDROID_KEYSTORE_BASE64` | *(Hasil encode base64 keystore)* | Binary keystore dalam bentuk string Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | *(Isi dari penyimpanan rahasia tim; jangan tulis nilainya di Git)* | Password keystore |
| `ANDROID_KEY_ALIAS` | `wastenot` | Alias key certificate |
| `ANDROID_KEY_PASSWORD` | *(Isi dari penyimpanan rahasia tim; jangan tulis nilainya di Git)* | Password alias key |

> **Cara mengambil `ANDROID_KEYSTORE_BASE64`:**
> Jalankan perintah berikut di root folder project:
> ```bash
> base64 -w 0 keystore/wastenot-release.jks
> ```
> *Salin satu baris string panjang yang dihasilkan dan tempelkan ke secret `ANDROID_KEYSTORE_BASE64`.*

---

## 4. Cara Menjalankan Pipeline CI/CD

### 1. Build & Release Otomatis via Git Tag (Direkomendasikan)
Buat tag rilis baru dan push ke GitHub:
```bash
git tag v1.0.1
git push origin v1.0.1
```
Workflow `Android Release & Deploy` akan otomatis berjalan, menguji unit test, mem-build signed APK `wastenot-v1.0.1.apk`, dan mengunggahnya ke VM.

### 2. Build & Release Otomatis via Push ke `main`
Setiap commit atau merge Pull Request ke branch `main` akan otomatis mem-build versi rilis dengan format `wastenot-v1.0.0-b{BUILD_NUMBER}.apk`.

### 3. Trigger Manual via GitHub Web UI
1. Masuk ke tab **Actions** di GitHub repository.
2. Pilih workflow **Android Release & Deploy** di bilah kiri.
3. Klik **Run workflow**.
4. (Opsional) Masukkan versi kustom (misal: `1.0.2-beta`) di input `Custom Version`.
5. Klik tombol hijau **Run workflow**.

### 4. Deploy Manual dari Terminal Developer (Opsional)
Jika ingin deploy langsung dari laptop tanpa menunggu GitHub Actions:
```bash
# Build signed release APK
make build-release

# Deploy langsung ke VM oktaavsm.bccdev.id via SSH pg-bcc
make deploy-vm
```

---

## 5. Verifikasi Hasil Distribusi

Setelah deployment selesai:

1. **Buka Landing Page di Browser**:
   [https://oktaavsm.bccdev.id/wastenot/](https://oktaavsm.bccdev.id/wastenot/)
   - Halaman akan menampilkan UI bertema hijau pinus dan lime dengan tombol download utama.
   - Tabel "Versi Tersedia" akan memuat maksimal 3 versi (versi terbaru dan maksimal 2 versi sebelumnya).

2. **Uji Direct Download via Terminal**:
   ```bash
   curl -I -k https://oktaavsm.bccdev.id/wastenot/download
   ```
   *Response header akan memuat `Content-Disposition: attachment; filename="wastenot-latest.apk"` dan status `HTTP/2 200`.*

3. **Cek API Metadata Versi (JSON)**:
   ```bash
   curl -s https://oktaavsm.bccdev.id/wastenot/versions.json
   ```
   *Menampilkan struktur JSON ringkas yang dapat digunakan untuk pengecekan in-app auto-update.*

## Validasi branch pengembangan

Branch `codex/**` menjalankan Android CI untuk lint, unit test dan build. Release/deployment tetap workflow tersendiri; push branch pengembangan tidak memicu deploy VM. Lihat [setup Android](ANDROID_SETUP.md) untuk konfigurasi API per build type serta batas pengujian yang belum dijalankan.

Runner dipin ke `ubuntu-24.04`, sesuai image yang lulus uji A01. Workflow memakai rilis Actions berbasis Node 24: checkout 7.0.1, setup-java 6.0.1, upload-artifact 7.0.2 dan setup-gradle 6.4.0. Pembaruan ini menanggapi peringatan runtime pada run CI; lihat [migrasi Node 20](https://github.blog/changelog/2025-09-19-deprecation-of-node-20-on-github-actions-runners/) dan [rencana migrasi ubuntu-latest](https://github.com/actions/runner-images/issues/14748).
