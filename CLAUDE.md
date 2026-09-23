# CLAUDE.md — WasteNot Android

Dokumen ini adalah panduan konteks utama untuk AI coding agent (Claude, Gemini, Cursor, dll.) yang bekerja di repo ini. Baca dokumen ini sebelum mulai menulis atau memodifikasi kode apapun.

## Proyek

**WasteNot** adalah aplikasi Android untuk pencatatan aktivitas pengelolaan sampah pribadi (Reduce, Reuse, Recycle, Trash). Ditulis dengan **Kotlin + Jetpack Compose + Navigation 3**, single-Activity, one Gradle module `:app`. Package: `com.example.wastenot`.

## Dokumen referensi wajib

Semua desain dan spesifikasi produk berada di [`docs/WasteNot-Product-Design-Kit/`](docs/WasteNot-Product-Design-Kit/). **Baca dokumen yang relevan sebelum menulis kode UI, API client, atau logika domain.**

| Dokumen | Kapan harus dibaca |
|---|---|
| [`DESIGN.md`](docs/WasteNot-Product-Design-Kit/DESIGN.md) | Setiap kali mengerjakan UI, theme, warna, tipografi, spacing, komponen, animasi, aksesibilitas |
| [`SCREEN_SPEC.md`](docs/WasteNot-Product-Design-Kit/SCREEN_SPEC.md) | Setiap kali membuat atau memodifikasi layar (S01–S20), komponen (C01–C34), atau dialog (D01–D04) |
| [`ARCHITECTURE.md`](docs/WasteNot-Product-Design-Kit/ARCHITECTURE.md) | Setiap kali menyentuh navigasi, ViewModel, Repository, state management, DI, atau storage |
| [`API.md`](docs/WasteNot-Product-Design-Kit/API.md) | Setiap kali membuat atau memodifikasi network layer, DTO, Retrofit interface, atau business rule |
| [`PRD.md`](docs/WasteNot-Product-Design-Kit/PRD.md) | Untuk memahami aturan domain: definisi metrik, unit, XP, Reduce vs Recycle, scope MVP |
| [`IMPLEMENTATION_PLAN.md`](docs/WasteNot-Product-Design-Kit/IMPLEMENTATION_PLAN.md) | Untuk prioritas tugas: backlog 4 minggu, gate per minggu, dan acceptance test |
| [`QA.md`](docs/WasteNot-Product-Design-Kit/QA.md) | Untuk memahami batas-batas yang sudah diverifikasi di prototype HTML |
| [`DEV_PLUGINS.md`](docs/DEV_PLUGINS.md) | Panduan setup, shortcuts, dan perintah plugin dev (HotSwan, NavGraph, ADB Idea, JSON to Kotlin) |

Untuk review visual interaktif: buka [`docs/WasteNot-Product-Design-Kit/design-system.html`](docs/WasteNot-Product-Design-Kit/design-system.html) di browser — tidak perlu build atau koneksi internet.

## Tech stack

| Komponen | Pilihan |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation 3 (`androidx.navigation3`) |
| State | ViewModel + StateFlow (UDF) |
| Network | Retrofit + OkHttp (belum disetup, lihat API.md) |
| Local storage | DataStore (preferences + draft) |
| DI | Constructor injection + AppContainer sederhana |
| AGP | 9.0.1, Gradle 9.1.0, Kotlin 2.3.20, Compose BOM 2026.03.01 |
| Min SDK | 24, Compile/Target SDK 36 |

## Plugin development

| Plugin | Fungsi | Konfigurasi |
|---|---|---|
| **Compose HotSwan v2** (`2.0.1`) | Hot reload Compose tanpa re-run | Di-apply di `app/build.gradle.kts`; butuh IDE plugin "Compose HotSwan" dari Marketplace |
| **Compose Navigation Graph** (`0.3.0`) | Visualisasi nav graph di IDE | Anotasi `@NavGraphRoot`, `@NavDestination`, `@NavPreview`; butuh IDE plugin "Compose Navigation Graph" |
| **KSP** (`2.3.12`) | Symbol processing untuk nav graph | Di-apply otomatis oleh navgraph plugin |

Untuk generate/export nav graph: `./gradlew :app:generateNavGraph` atau `./gradlew :app:exportNavGraphHtml`.

## Aturan penting

### Desain & UI
- Semua token warna, typography, dan spacing **harus** mengacu ke [`DESIGN.md §2–4`](docs/WasteNot-Product-Design-Kit/DESIGN.md). Jangan hardcode hex color di composable.
- Theme Material 3: `WasteNotTheme` (di `app/src/main/java/com/example/wastenot/theme/`). Dynamic color **dimatikan**.
- Font: **Plus Jakarta Sans** saja. File font ada di [`docs/WasteNot-Product-Design-Kit/assets/fonts/`](docs/WasteNot-Product-Design-Kit/assets/fonts/).
- Touch target minimal **48 dp**. Form tidak boleh tertutup keyboard. Edge-to-edge dengan inset yang benar.
- Warna dock navigasi: glassmorphism/kaca sesuai [`DESIGN.md §2 Material v1.1`](docs/WasteNot-Product-Design-Kit/DESIGN.md). Baseline opak tetap harus berfungsi.

### Domain & metrik
- **Reduce** = benda yang dihindari (avoided_count). Tidak menambah total sampah yang ditangani.
- **Recycling rate** = weight_g Recycle / total weight_g semua metode selain Reduce. Null jika tidak ada data, bukan 0 atau NaN.
- **XP**: 10 per log; deduct saat delete. Hitung dari catatan aktif, bukan cache tersimpan.
- Unit: berat dalam **gram** (integer), jumlah benda integer. Jangan konversi count ke kilogram.
- Semua angka penting berasal dari server, bukan dihitung ulang di client secara mandiri.

### Arsitektur
- Satu Activity (`MainActivity`), satu module (`:app`), package by feature.
- ViewModel + StateFlow + UDF. Jangan simpan context, Activity, atau NavController di ViewModel.
- Route hanya berisi primitive/serializable ID, bukan seluruh objek.
- Password/refresh token: **hanya** di Android Keystore / secure storage. Jangan di DataStore atau SavedStateHandle.
- Retrofit interceptor: log hanya status/durasi/request-id, tidak pernah log Authorization header atau payload pribadi.

### Kualitas & safety
- Semua teks produksi di `strings.xml`, label berbahasa Indonesia.
- `contentDescription` untuk semua ikon aksi. `null` hanya untuk ikon dekoratif yang sudah ada label teksnya.
- Error jaringan tidak membuang data yang sudah ditampilkan. Retry idempoten dengan `client_request_id`.
- Jangan tampilkan keberhasilan sebelum konfirmasi dari server.

## Struktur file yang disarankan (lihat ARCHITECTURE.md §3)

```
app/src/main/java/com/example/wastenot/
  MainActivity.kt
  AppContainer.kt
  navigation/          Route.kt, AppNavigation.kt
  ui/theme/            Color.kt, Type.kt, Shape.kt, Dimens.kt, Theme.kt
  ui/components/       WasteLogRow.kt, MethodChip.kt, QuantityField.kt, ...
  feature/auth/        LoginRoute.kt, LoginScreen.kt, AuthViewModel.kt
  feature/home/        HomeRoute.kt, HomeScreen.kt, HomeViewModel.kt
  feature/log/         History*, LogDetail*, LogEditor*, FilterState.kt
  feature/guide/       GuideList*, GuideDetail*
  feature/statistics/  Statistics*
  feature/profile/     Profile*, Achievement*, Settings*
  data/network/        WasteNotApi.kt, dto/, AuthInterceptor.kt
  data/repository/     AuthRepository.kt, LogRepository.kt, ...
  data/local/          PreferencesStore.kt, DraftStore.kt, TokenStore.kt
  model/               WasteLog.kt, WasteCategory.kt, DisposalMethod.kt
```

## Perintah berguna

```bash
./gradlew assembleDebug             # Build APK debug
./gradlew test                      # Unit test
./gradlew :app:generateNavGraph     # Generate nav graph data
./gradlew :app:exportNavGraphHtml   # Export nav graph HTML interaktif
./gradlew :app:exportNavGraphMermaid # Export diagram Mermaid
./gradlew :app:navLint              # Cek navigasi unreachable/masalah
./gradlew :app:navDump              # Commit baseline .nav
./gradlew :app:navCheck             # Cek apakah nav graph sudah sesuai baseline
```

## Apa yang TIDAK boleh dilakukan

- Jangan tambahkan feature module terpisah, BaseViewModel generik, atau use case 1:1 per endpoint (YAGNI).
- Jangan gunakan warna, font, atau identitas visual dari Titip.in (project lain).
- Jangan hardcode base URL API di composable atau ViewModel.
- Jangan simpan data sensitif di log, screenshot, atau Markdown.
- Jangan tampilkan angka CO₂ atau "dampak terverifikasi" — WasteNot mencatat kebiasaan, bukan mengklaim dampak ilmiah.
- Jangan gabungkan dua navigation library (hanya Navigation 3).
