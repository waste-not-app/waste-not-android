# AGENT.md — WasteNot Android

Panduan untuk AI coding agent yang bekerja di repositori ini. Dibaca bersama [`CLAUDE.md`](CLAUDE.md).

## Konteks proyek

**WasteNot** adalah aplikasi Android pencatatan sampah pribadi (3R: Reduce, Reuse, Recycle + Trash) dengan **AI-assisted waste detection** menggunakan YOLO + LLM recommendation pipeline. Stack: Kotlin, Jetpack Compose, Navigation 3, Material 3, CameraX. Single module `:app`. Package: `com.example.wastenot`.

## Di mana membaca spesifikasi

**Seluruh spesifikasi produk ada di [`docs/WasteNot-Product-Design-Kit update/WasteNot/`](docs/WasteNot-Product-Design-Kit%20update/WasteNot/) · v1.3 · 28 Sep 2026**

```
docs/WasteNot-Product-Design-Kit update/WasteNot/
  README.md              → Ikhtisar dan cara pakai design kit v1.3
  AI_SPEC.md           ★ → Spesifikasi AI pipeline: YOLO, mapping, LLM, privasi, state machine
  PRD.md                 → Product requirements v1.2 incl. AI scan scope
  DESIGN.md              → Design system v1.3: warna, font, spacing, komponen C01–C39 (incl. AI)
  SCREEN_SPEC.md         → Layar S01–S25 (incl. AI scan S21–S25), komponen C01–C39, dialog
  ARCHITECTURE.md        → Arsitektur Android v1.2 incl. §13 AI extension (ScanViewModel, Python worker)
  API.md                 → Data model v1.2: kontrak API core + AI endpoints §10
  IMPLEMENTATION_PLAN.md → Backlog 4 minggu core + AI stages AI-01–AI-08, AT-AI-01–18
  QA.md                  → Hasil QA prototype HTML, batas validasi
  design-system.html     → Mockup interaktif + AI scan flow (buka di browser)
  verify-ai.cjs        ★ → Automated test khusus AI scan flow
  assets/preview-ai.png ★ → Preview layar AI scan
  sources/AI_PLAN_ORIGINAL.md ★ → Dokumen source rencana tim (referensi, bukan spec final)
  assets/fonts/          → Plus Jakarta Sans variable font + lisensi SIL OFL
```

## Prioritas membaca berdasarkan task

| Task yang diminta | Dokumen yang harus dibaca dulu |
|---|---|
| Membuat / memodifikasi UI, screen, komponen | `DESIGN.md`, `SCREEN_SPEC.md` |
| Setup theme, warna, typography, spacing | `DESIGN.md §2–4` |
| Navigasi, route baru, back stack | `ARCHITECTURE.md §6`, `SCREEN_SPEC.md §2` |
| ViewModel, state, repository | `ARCHITECTURE.md §3–5` |
| Network layer, Retrofit, DTO | `API.md`, `ARCHITECTURE.md §8` |
| Domain logic (XP, recycling rate, Reduce) | `PRD.md §6–9` |
| Fitur auth, token, sesi | `ARCHITECTURE.md §8`, `API.md §3` |
| Accessibility, font scale, insets | `DESIGN.md §9`, `ARCHITECTURE.md §7` |
| Setup plugin dev, hot reload, nav graph | `docs/DEV_PLUGINS.md` |
| Acceptance test, QA criteria | `IMPLEMENTATION_PLAN.md §3–4`, `QA.md` |
| **★ Fitur AI scan, kamera, YOLO** | `AI_SPEC.md`, `SCREEN_SPEC.md §3.21–25`, `ARCHITECTURE.md §13` |
| **★ API AI (detections, recommendations)** | `API.md §10`, `AI_SPEC.md §6` |
| **★ Komponen AI (C35–C39)** | `SCREEN_SPEC.md §4`, `DESIGN.md §6` |
| **★ ScanViewModel, CameraX integration** | `AI_SPEC.md §3`, `ARCHITECTURE.md §13` |
| **★ Privacy / foto cleanup rules** | `AI_SPEC.md §8` |
| **★ Release gate / acceptance AI** | `IMPLEMENTATION_PLAN.md §10`, `AI_SPEC.md §9` |
| **★ CI/CD, deployment VM, & release** | `docs/CICD_SETUP.md`, `.github/workflows/` |

## Aturan singkat untuk agent

1. **Jangan tulis kode UI tanpa membaca `DESIGN.md` dulu.** Token warna, font, spacing, radius — semua ada di sana.
2. **Jangan buat layar baru tanpa cek `SCREEN_SPEC.md`.** S01–S25 sudah punya field, state, dan aksi yang didefinisikan.
3. **Jangan buat fitur AI scan tanpa baca `AI_SPEC.md` dulu.** Privacy, confidence bands, LLM output validation, REDUCE restriction — ada di sana.
4. **UDF ketat**: state hanya mengalir satu arah. ViewModel tidak boleh menyimpan Activity/NavController/Context.
5. **Idempoten**: setiap write request ke API harus disertai `client_request_id`. Retry tidak menciptakan record ganda.
6. **Bahasa UI**: Indonesia. Semua string produksi di `strings.xml`.
7. **Jangan komentar AI-slop**: jangan tulis komentar seperti `// This function handles...`, `// We then...`. Komentar hanya untuk hal yang tidak jelas dari kode itu sendiri.
8. **YAGNI**: jangan tambahkan abstraksi yang belum dibutuhkan (BaseViewModel, use case 1:1 per endpoint, feature modules).
9. **AI hanya support**: jangan auto-save log. Jangan rekomendasikan REDUCE dari foto. User selalu konfirmasi.

## Toolchain dev yang sudah terpasang

```bash
# Build & test
./gradlew assembleDebug
./gradlew test

# Navigation graph
./gradlew :app:generateNavGraph      # Generate data nav graph
./gradlew :app:exportNavGraphHtml    # Export HTML interaktif → app/build/navgraph/nav-graph.html
./gradlew :app:exportNavGraphMermaid # Export diagram Mermaid
./gradlew :app:navLint               # Cek masalah navigasi
./gradlew :app:navDump               # Commit .nav baseline
./gradlew :app:navCheck              # Validasi terhadap baseline
```

**IDE plugins yang harus di-install di Android Studio:**
- **Compose HotSwan** (Marketplace) → hot reload Compose; setelah run tekan Ctrl+S untuk lihat perubahan instan
- **Compose Navigation Graph** (Marketplace) → View > Tool Windows > NavGraph Graph

## Anotasi navigasi yang sudah dipakai

```kotlin
// Di Navigation.kt — root navigasi
@NavGraphRoot
@Composable fun MainNavigation() { ... }

// Di setiap Screen — tujuan navigasi
@NavDestination(route = Main::class)
@Composable fun MainScreen(...) { ... }

// Di Preview — thumbnail untuk nav graph
@NavPreview(route = Main::class)
@Preview @Composable fun MainScreenPreview() { ... }
```

## File kunci

| File | Isi |
|---|---|
| [`CLAUDE.md`](CLAUDE.md) | Panduan utama untuk AI agent (konteks lengkap + aturan AI) |
| [`AGENT.md`](AGENT.md) | Dokumen ini — quick reference untuk agent |
| [`docs/WasteNot-Product-Design-Kit update/WasteNot/AI_SPEC.md`](docs/WasteNot-Product-Design-Kit%20update/WasteNot/AI_SPEC.md) | ★ Spesifikasi AI pipeline lengkap |
| [`docs/WasteNot-Product-Design-Kit update/WasteNot/README.md`](docs/WasteNot-Product-Design-Kit%20update/WasteNot/README.md) | Ikhtisar design kit dan tautan spesifikasi |
| [`gradle/libs.versions.toml`](gradle/libs.versions.toml) | Version catalog semua dependency |
| [`app/build.gradle.kts`](app/build.gradle.kts) | Module config, semua plugin diterapkan di sini |
| [`app/src/main/java/com/example/wastenot/MainActivity.kt`](app/src/main/java/com/example/wastenot/MainActivity.kt) | Entry point |
| [`app/src/main/java/com/example/wastenot/Navigation.kt`](app/src/main/java/com/example/wastenot/Navigation.kt) | NavDisplay root dan back stack |
| [`app/src/main/java/com/example/wastenot/NavigationKeys.kt`](app/src/main/java/com/example/wastenot/NavigationKeys.kt) | NavKey data objects untuk semua route |
