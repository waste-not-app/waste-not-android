# WasteNot Android

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Navigation](https://img.shields.io/badge/Navigation-Navigation_3-brightgreen.svg)](https://developer.android.com/guide/navigation/navigation-3)
[![AGP](https://img.shields.io/badge/AGP-9.0.1-orange.svg)](https://developer.android.com/studio/releases/gradle-plugin)
[![Gradle](https://img.shields.io/badge/Gradle-9.1.0-02303A.svg)](https://gradle.org)

**WasteNot** adalah aplikasi Android untuk pencatatan dan pelacakan kebiasaan pengelolaan sampah pribadi berdasarkan prinsip **3R (Reduce, Reuse, Recycle)** dan Trash. Dilengkapi fitur **AI-assisted waste detection** menggunakan YOLO object detection dan LLM recommendation untuk membantu pengguna mengidentifikasi jenis sampah dan metode penanganannya. AI berfungsi sebagai asisten; keputusan akhir tetap di tangan pengguna.

---

## 📱 Tech Stack & Arsitektur

*   **Language**: [Kotlin](https://kotlinlang.org/) `2.3.20`
*   **UI Toolkit**: [Jetpack Compose](https://developer.android.com/jetpack/compose) + [Material 3](https://m3.material.io/)
*   **Typography**: Plus Jakarta Sans (Variable Font)
*   **Navigation**: [Jetpack Navigation 3](https://developer.android.com/guide/navigation/navigation-3) (`androidx.navigation3`)
*   **Architecture Pattern**: Single-Activity, Unidirectional Data Flow (UDF), ViewModel + StateFlow
*   **Camera**: [CameraX](https://developer.android.com/training/camerax) (lifecycle-aware, untuk AI scan flow)
*   **AI Pipeline**: YOLO object detection + LLM recommendation via backend inference service
*   **Build Toolchain**: Android Gradle Plugin (AGP) `9.0.1`, Gradle `9.1.0`, JDK 17
*   **SDK Target**: Compile SDK `36`, Target SDK `36`, Min SDK `24`
*   **Dev Tools**: Hot-reload via [Compose HotSwan v2](https://hotswan.dev), visualisasi nav via [Compose Navigation Graph](https://skydoves.github.io/compose-nav-graph)

---

## ⚡ Quick Start

### 1. Prasyarat
*   **Android Studio** (Ladybug / terbaru disarankan)
*   **JDK 17+**
*   **Android SDK Platform 36**
*   **Git** & **GNU Make**

### 2. Clone & Setup
```bash
git clone git@github.com:waste-not-app/waste-not-android.git
cd waste-not-android

# Jalankan setup awal (cek tools, konfigurasi branch develop, pasang git pre-commit hook)
make setup
```

### 3. Build & Run
```bash
# Build APK debug
make build
# Output: app/build/outputs/apk/debug/app-debug.apk

# Jalankan langsung di emulator / device fisik aktif
make run

# Jalankan semua unit test
make test

# Validasi menyeluruh (Lint + Unit Test)
make check
```

---

## 🚀 CI/CD & Distribusi APK

Aplikasi ini dilengkapi pipeline otomatis GitHub Actions untuk build, testing, dan rilis langsung ke server:
*   **Landing Page**: [https://oktaavsm.bccdev.id/wastenot/](https://oktaavsm.bccdev.id/wastenot/)
*   **Direct Download (Force Download APK Terbaru)**: [https://oktaavsm.bccdev.id/wastenot/download](https://oktaavsm.bccdev.id/wastenot/download)
*   **Retensi Otomatis**: Menyimpan maksimal **3 versi** rilis (versi terbaru + 2 versi sebelumnya). Build terlama otomatis dihapus saat rilis baru diunggah.
*   **Deploy Manual**: Tersedia target `make deploy-vm` untuk deploy cepat langsung dari lokal via SSH `pg-bcc`.

👉 Panduan konfigurasi GitHub Repository Secrets & panduan rilis tersedia di [**`docs/CICD_SETUP.md`**](docs/CICD_SETUP.md).

---

## 🛠️ Plugin Development & Tooling

Proyek ini telah dilengkapi dengan plugin pengembang modern untuk memaksimalkan produktivitas tim:

1.  **Compose HotSwan v2 (`2.0.1`)**: Hot-reload UI Compose seketika dalam hitungan milidetik pada device/emulator saat menyimpan file (`Ctrl + S` / `Cmd + S`) tanpa merestart aplikasi atau kehilangan state input/navigasi.
2.  **Compose Navigation Graph (`0.3.0`) + KSP (`2.3.12`)**: Menganalisis alur navigasi Compose secara statis dan merender graph visual interaktif lengkap dengan thumbnail `@Preview`.
3.  **ADB Idea**: Shortcut cepat manipulasi app di device (`Ctrl + Alt + Shift + A` / `Cmd + Shift + A`).
4.  **JSON To Kotlin Class**: Generate `data class` Kotlin bertipe `@Serializable` dari response JSON backend (`Alt + K`).

👉 Panduan detail instalasi IDE plugin, konfigurasi, dan cheatsheet shortcut tersedia di [**`docs/DEV_PLUGINS.md`**](docs/DEV_PLUGINS.md).

---

## 🌿 Branching Strategy & Workflow Tim

Proyek mengadopsi alur kerja Git berbasis sprint mingguan yang terintegrasi dengan target `Makefile`:

```text
main (production-ready)
  └── develop (integrasi harian)
        ├── feature/W{week}-{ticket}_{name}   (fitur baru)
        ├── fix/W{week}-{ticket}_{name}       (perbaikan bug sprint)
        ├── release/week-{week}               (stabilisasi akhir minggu)
        └── hotfix/{ticket}_{name}            (perbaikan kritis langsung dari main)
```

### Perintah Branching
```bash
# Buat feature branch dari develop
make branch-feature TICKET=W1-03 NAME=theme-setup

# Buat fix branch dari develop
make branch-fix TICKET=W2-04 NAME=conflict-409

# Cek kesiapan sebelum push PR (lint + test + nav-check)
make pr-ready

# Merge interaktif ke develop
make merge-dev
```

### Sprint Gates (dari IMPLEMENTATION_PLAN.md)
```bash
make week1    # Validasi Gate Minggu 1: Fondasi & Autentikasi
make week2    # Validasi Gate Minggu 2: Tracking CRUD Lengkap
make week3    # Validasi Gate Minggu 3: Panduan & Evaluasi Statistik
make week4    # Validasi Gate Minggu 4: Ketahanan, Aksesibilitas & Rilis Demo
```

---

## 🗺️ Navigation Graph Commands

```bash
make nav-graph      # Ekstraksi graph navigasi ke app/build/navgraph/nav-graph.json
make nav-html       # Ekspor graph HTML interaktif dan otomatis buka di browser
make nav-mermaid    # Ekspor representasi diagram Mermaid untuk GitHub Markdown
make nav-lint       # Validasi integritas rute navigasi (deteksi rute mati/unreachable)
make nav-dump       # Simpan baseline navigasi (.nav)
make nav-check      # Validasi branch terhadap baseline .nav (digunakan pada CI)
```

---

## 📂 Struktur Proyek

```text
wastenot-android/
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/com/example/wastenot/
│       │   │   ├── MainActivity.kt        # Single activity host
│       │   │   ├── Navigation.kt          # Navigation 3 NavDisplay & root backstack
│       │   │   ├── NavigationKeys.kt      # NavKey routes & data objects
│       │   │   ├── theme/                 # Material 3 ColorScheme, Typography, Theme
│       │   │   ├── ui/main/               # Main screen Composable & ViewModel
│       │   │   ├── feature/scan/          # ★ ScanBottomSheet, ScanViewModel (AI)
│       │   │   ├── data/ai/               # ★ AiRepository, AI DTO (AI)
│       │   │   └── core/camera/           # ★ CameraManager, ImageCaptureHelper (AI)
│       │   └── AndroidManifest.xml
│       └── test/                          # Unit tests
├── .github/
│   └── workflows/                         # GitHub Actions CI/CD workflows
│       ├── android-ci.yml                 # Test & build APK pada PR/push
│       └── android-release.yml            # Build, sign, & deploy APK ke VM
├── docs/
│   ├── CICD_SETUP.md                   ★  # Panduan setup GitHub Secrets & rilis APK
│   ├── DEV_PLUGINS.md                     # Panduan setup HotSwan, NavGraph, dan tooling
│   └── design-kit/                        # Dokumen spesifikasi produk & design kit v1.3
│       ├── design-system.html             # Mockup visual & prototype interaktif incl. AI (buka di browser)
│       ├── AI_SPEC.md                   ★ # Spesifikasi AI pipeline (YOLO/Guide/LLM)
│       ├── DESIGN.md                      # Design system v1.3, komponen C01–C39
│       ├── SCREEN_SPEC.md                 # Spesifikasi layar S01–S25 (incl. AI), C01–C39
│       ├── PRD.md                         # Product Requirements v1.2, domain rules, AI scope
│       ├── ARCHITECTURE.md                # Arsitektur Android v1.2, §13 AI extension
│       ├── API.md                         # Kontrak API v1.2 core + §10 AI endpoints
│       ├── IMPLEMENTATION_PLAN.md         # Backlog 4 minggu + §10 AI stages & acceptance tests
│       ├── AGENT_INDEX.md                 # Indeks referensi cepat pencarian topik (incl. AI)
│       ├── verify-ai.cjs                ★ # Automated test AI scan flow
│       └── sources/AI_PLAN_ORIGINAL.md  ★ # Source rencana tim (referensi)
├── scripts/
│   └── manage_releases.py                 # Script retensi (max 3 versi) & generator landing page
├── gradle/
│   └── libs.versions.toml                 # Version Catalog dependensi & plugin
├── Makefile                               # Otomasi build, test, branching, deploy, dan nav tools
├── CLAUDE.md                              # Konteks proyek & aturan AI untuk AI coding assistant
├── AGENT.md                               # Panduan cepat developer & agent
└── settings.gradle.kts
```

---

## 📖 Dokumentasi Spesifikasi

Seluruh dokumen produk, desain, arsitektur, dan CI/CD tersimpan di folder [`docs/`](docs/):

| Dokumen | Deskripsi |
|---|---|
| [**`CICD_SETUP.md`**](docs/CICD_SETUP.md) | ★ **Baru** — Panduan lengkap setup GitHub Secrets, konfigurasi SSH VM, pipeline rilis otomatis, dan retensi 3 versi APK. |
| [**`AI_SPEC.md`**](docs/design-kit/AI_SPEC.md) | Spesifikasi penuh AI pipeline: YOLO object detection, confidence bands, waste mapping, LLM recommendation, state machine kamera, privasi & retensi foto, release gates. |
| [**`DESIGN.md`**](docs/design-kit/DESIGN.md) | Panduan visual v1.3: palet warna pinus–lime, tipografi Plus Jakarta Sans, spacing, material kaca navigasi, komponen AI (C35–C39), dan handoff Compose. |
| [**`SCREEN_SPEC.md`**](docs/design-kit/SCREEN_SPEC.md) | Spesifikasi interaksi dan UI untuk seluruh 25 layar (`S01–S25` termasuk AI scan `S21–S25`), komponen (`C01–C39`), dan dialog (`D01–D04`). |
| [**`PRD.md`**](docs/design-kit/PRD.md) | Kebutuhan produk v1.2, aturan metrik (gram vs benda, recycling rate, XP), dan AI increment scope. |
| [**`ARCHITECTURE.md`**](docs/design-kit/ARCHITECTURE.md) | Panduan arsitektur Android v1.2 + §13 AI extension: ScanViewModel, Python YOLO worker, AI config vars, CI tambahan. |
| [**`API.md`**](docs/design-kit/API.md) | Kontrak API v1.2: endpoint core + §10 AI endpoints (detections, recommendations), ai_provenance, error codes AI. |
| [**`IMPLEMENTATION_PLAN.md`**](docs/design-kit/IMPLEMENTATION_PLAN.md) | Roadmap 4 minggu core + §10 AI increment stages `AI-01–AI-08`, acceptance tests `AT-01–AT-26` + `AT-AI-01–18`. |
| [**`design-system.html`**](docs/design-kit/design-system.html) | Prototype visual interaktif termasuk AI scan flow — dapat langsung dibuka di browser tanpa server. |
| [**`DEV_PLUGINS.md`**](docs/DEV_PLUGINS.md) | Panduan instalasi dan penggunaan plugin developer (HotSwan, NavGraph, ADB Idea). |
| [**`AGENT_INDEX.md`**](docs/design-kit/AGENT_INDEX.md) | Indeks cepat semua topik → section dokumen, termasuk AI. |

---

## 🤝 Kontribusi & Workflow

1. Sebelum membuat perubahan UI atau fitur, selalu baca dokumen terkait di [`docs/design-kit/`](docs/design-kit/).
2. Buat branch baru dari `develop` menggunakan `make branch-feature TICKET=... NAME=...`.
3. Jalankan `make check` sebelum commit (pre-commit hook akan otomatis memvalidasi lint dan test).
4. Jalankan `make pr-ready` sebelum mengajukan Pull Request ke `develop`.
