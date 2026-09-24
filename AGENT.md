# AGENT.md — WasteNot Android

Panduan untuk AI coding agent yang bekerja di repositori ini. Dibaca bersama [`CLAUDE.md`](CLAUDE.md).

## Konteks proyek

**WasteNot** adalah aplikasi Android pencatatan sampah pribadi (3R: Reduce, Reuse, Recycle + Trash). Stack: Kotlin, Jetpack Compose, Navigation 3, Material 3. Single module `:app`. Package: `com.example.wastenot`.

## Di mana membaca spesifikasi

**Seluruh spesifikasi produk ada di [`docs/design-kit/`](docs/design-kit/).**

```
docs/design-kit/
  README.md              → Ikhtisar dan cara pakai design kit
  PRD.md                 → Product requirements, aturan domain, metrik, scope MVP
  DESIGN.md              → Design system: warna, font, spacing, komponen, aksesibilitas
  SCREEN_SPEC.md         → Spesifikasi layar S01–S20, komponen C01–C34, dialog D01–D04
  ARCHITECTURE.md        → Arsitektur Android: navigasi, state, network, security
  API.md                 → Data model, kontrak API, DTO, enum, konvensi
  IMPLEMENTATION_PLAN.md → Backlog 4 minggu, gate, dan acceptance test
  QA.md                  → Hasil QA prototype HTML, batas validasi
  design-system.html     → Mockup interaktif + galeri semua layar (buka di browser)
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

## Aturan singkat untuk agent

1. **Jangan tulis kode UI tanpa membaca `DESIGN.md` dulu.** Token warna, font, spacing, radius — semua ada di sana.
2. **Jangan buat layar baru tanpa cek `SCREEN_SPEC.md`.** S01–S20 sudah punya field, state, dan aksi yang didefinisikan.
3. **UDF ketat**: state hanya mengalir satu arah. ViewModel tidak boleh menyimpan Activity/NavController/Context.
4. **Idempoten**: setiap write request ke API harus disertai `client_request_id`. Retry tidak menciptakan record ganda.
5. **Bahasa UI**: Indonesia. Semua string produksi di `strings.xml`.
6. **Jangan komentar AI-slop**: jangan tulis komentar seperti "// This function handles...", "// We then...", dsb. Komentar hanya untuk hal yang tidak jelas dari kode itu sendiri.
7. **YAGNI**: jangan tambahkan abstraksi yang belum dibutuhkan (BaseViewModel, use case 1:1 per endpoint, feature modules).

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
| [`CLAUDE.md`](CLAUDE.md) | Panduan utama untuk AI agent (konteks lengkap) |
| [`AGENT.md`](AGENT.md) | Dokumen ini — quick reference untuk agent |
| [`gradle/libs.versions.toml`](gradle/libs.versions.toml) | Version catalog semua dependency |
| [`app/build.gradle.kts`](app/build.gradle.kts) | Module config, semua plugin diterapkan di sini |
| [`app/src/main/java/com/example/wastenot/MainActivity.kt`](app/src/main/java/com/example/wastenot/MainActivity.kt) | Entry point |
| [`app/src/main/java/com/example/wastenot/Navigation.kt`](app/src/main/java/com/example/wastenot/Navigation.kt) | NavDisplay root dan back stack |
| [`app/src/main/java/com/example/wastenot/NavigationKeys.kt`](app/src/main/java/com/example/wastenot/NavigationKeys.kt) | NavKey data objects untuk semua route |
