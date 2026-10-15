# CLAUDE.md — WasteNot Android

Dokumen ini adalah panduan konteks utama untuk AI coding agent (Claude, Gemini, Cursor, dll.) yang bekerja di repo ini. Baca dokumen ini sebelum mulai menulis atau memodifikasi kode apapun.

## Proyek

**WasteNot** adalah aplikasi Android untuk pencatatan aktivitas pengelolaan sampah pribadi (Reduce, Reuse, Recycle, Trash), diperluas dengan **AI-assisted waste detection** menggunakan YOLO + LLM recommendation pipeline. Ditulis dengan **Kotlin + Jetpack Compose + Navigation 3**, single-Activity, one Gradle module `:app`. Package: `com.example.wastenot`.

## Dokumen referensi wajib

Semua desain dan spesifikasi produk berada di [`docs/WasteNot-Product-Design-Kit update/WasteNot/`](docs/WasteNot-Product-Design-Kit%20update/WasteNot/) **(v1.3 · 28 Sep 2026)**. **Baca dokumen yang relevan sebelum menulis kode UI, API client, atau logika domain.**

| Dokumen | Kapan harus dibaca |
|---|---|
| [`AI_SPEC.md`](docs/WasteNot-Product-Design-Kit%20update/WasteNot/AI_SPEC.md) | ★ Setiap kali menyentuh fitur AI: kamera, YOLO inference, LLM, mapping, confidence, state machine, privasi |
| [`DESIGN.md`](docs/WasteNot-Product-Design-Kit%20update/WasteNot/DESIGN.md) | Setiap kali mengerjakan UI, theme, warna, tipografi, spacing, komponen (incl. C35–C39 AI), animasi, aksesibilitas |
| [`SCREEN_SPEC.md`](docs/WasteNot-Product-Design-Kit%20update/WasteNot/SCREEN_SPEC.md) | Setiap kali membuat/memodifikasi layar (S01–S25 incl. AI scan S21–S25), komponen (C01–C39), atau dialog |
| [`ARCHITECTURE.md`](docs/WasteNot-Product-Design-Kit%20update/WasteNot/ARCHITECTURE.md) | Setiap kali menyentuh navigasi, ViewModel, Repository, state management, DI, storage, atau AI extension §13 |
| [`API.md`](docs/WasteNot-Product-Design-Kit%20update/WasteNot/API.md) | Setiap kali membuat/memodifikasi network layer, DTO, Retrofit interface, business rule, atau AI endpoints §10 |
| [`PRD.md`](docs/WasteNot-Product-Design-Kit%20update/WasteNot/PRD.md) | Untuk memahami aturan domain: definisi metrik, unit, XP, Reduce vs Recycle, scope MVP + AI increment |
| [`IMPLEMENTATION_PLAN.md`](docs/WasteNot-Product-Design-Kit%20update/WasteNot/IMPLEMENTATION_PLAN.md) | Untuk prioritas tugas: backlog 4 minggu core + AI stages AI-01–AI-08, acceptance tests |
| [`QA.md`](docs/WasteNot-Product-Design-Kit%20update/WasteNot/QA.md) | Untuk memahami batas-batas yang sudah diverifikasi di prototype HTML |
| [`DEV_PLUGINS.md`](docs/DEV_PLUGINS.md) | Panduan setup, shortcuts, dan perintah plugin dev (HotSwan, NavGraph, ADB Idea, JSON to Kotlin) |
| [`CICD_SETUP.md`](docs/CICD_SETUP.md) | Panduan setup GitHub Secrets, pipeline CI/CD GitHub Actions, dan deployment ke VM oktaavsm.bccdev.id |

Untuk review visual interaktif (termasuk AI scan flow): buka [`docs/WasteNot-Product-Design-Kit update/WasteNot/design-system.html`](docs/WasteNot-Product-Design-Kit%20update/WasteNot/design-system.html) di browser — tidak perlu build atau koneksi internet.

## Kondisi implementasi

Startup, form Masuk/Daftar, sesi Keystore/refresh, lima back stack, tema/form bersama, editor manual dan logout sudah terhubung. Mode contoh dipilih eksplisit dan menyediakan pencatatan/edit/statistik lokal berlabel. Adapter HTTP tersedia; write aktivitas/ringkasan backend nyata, konten panduan dan AI masih menunggu integrasi. Untuk batas implementasi dan bukti pengujian, baca [catatan task](docs/tasks/README.md) serta [QA Iteration 1](docs/tasks/ITERATION_1_QA.md).

## Tech stack

| Komponen | Pilihan |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation 3 (`androidx.navigation3`) |
| State | ViewModel + StateFlow (UDF) |
| Network | Retrofit + OkHttp (lihat API.md) |
| Camera | CameraX (ImageCapture + lifecycle-aware, untuk AI scan) |
| Local storage | SharedPreferences untuk tema, Saver untuk draf UI, Keystore untuk refresh token |
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
- Semua token warna, typography, dan spacing **harus** mengacu ke [`DESIGN.md §2–4`](docs/WasteNot-Product-Design-Kit%20update/WasteNot/DESIGN.md). Jangan hardcode hex color di composable.
- Theme Material 3: `WasteNotTheme` (di `app/src/main/java/com/example/wastenot/theme/`). Dynamic color **dimatikan**.
- Font: **Plus Jakarta Sans** saja. File font ada di [`docs/WasteNot-Product-Design-Kit update/WasteNot/assets/fonts/`](docs/WasteNot-Product-Design-Kit%20update/WasteNot/assets/fonts/).
- Touch target minimal **48 dp**. Form tidak boleh tertutup keyboard. Edge-to-edge dengan inset yang benar.
- Warna dock navigasi: glassmorphism/kaca sesuai [`DESIGN.md §2 Material v1.1`](docs/WasteNot-Product-Design-Kit%20update/WasteNot/DESIGN.md). Baseline opak tetap harus berfungsi.

### Domain & metrik
- **Reduce** = benda yang dihindari (avoided_count). Tidak menambah total sampah yang ditangani.
- **Recycling rate** = jumlah aktivitas RECYCLE / jumlah aktivitas REUSE+RECYCLE+TRASH × 100. Denominator nol menghasilkan null. Berat/count/Reduce tetap terpisah; aturan kanonik ada di PRD §9.2.
- **XP**: 10 per log; deduct saat delete. Hitung dari catatan aktif, bukan cache tersimpan.
- Unit: berat dalam **gram** (integer), jumlah benda integer. Jangan konversi count ke kilogram.
- Semua angka penting berasal dari server, bukan dihitung ulang di client secara mandiri.

### AI — aturan kritis
- **AI adalah decision support, bukan pengambil keputusan.** Pengguna wajib konfirmasi sebelum log disimpan.
- **REDUCE tidak boleh direkomendasikan dari scan foto.** Hanya tersedia via input manual (tanpa ai_provenance).
- **Tombol Catat/Scan = action di tengah atas dock, bukan tab ke-6.** Lima tab tetap: Home, History, Guide, Statistics, Profile.
- Camera scan membuka `ModalBottomSheet` multi-step (S21–S25), bukan layar/route baru.
- Foto sementara scan **harus dihapus** saat keluar alur, logout, atau paling lambat 1 jam. Jangan simpan permanen kecuali user opt-in foto lampiran.
- Jangan kirim foto, nama, email, lokasi, atau token ke LLM. Kirim hanya: object_key, category, condition context, potongan panduan, allowed methods.
- Output LLM **wajib divalidasi server** (enum, JSON schema, evidence check). Jangan percaya output raw dari LLM di client.
- `recommended_method` yang invalid → respons UNAVAILABLE, method null, user pilih manual.
- Confidence bands: ≥0.70 boleh ditawarkan; 0.50–0.69 tampilkan "belum yakin"; <0.50 → manual required.
- `ScanViewModel` hanya menyimpan immutable state. Tidak menyimpan Activity/Context/Camera reference.

### Arsitektur
- Satu Activity (`MainActivity`), satu module (`:app`), package by feature.
- ViewModel + StateFlow + UDF. Jangan simpan context, Activity, atau NavController di ViewModel.
- Route hanya berisi primitive/serializable ID, bukan seluruh objek.
- Password hanya in-memory selama form aktif. Access token di memori; refresh token memakai penyimpanan berbasis Android Keystore dan dikecualikan backup. DataStore/SavedStateHandle hanya untuk state nonrahasia.
- Retrofit interceptor: log hanya status/durasi/request-id, tidak pernah log Authorization header atau payload pribadi.

### Kualitas & safety
- Semua teks produksi di `strings.xml`, label berbahasa Indonesia.
- `contentDescription` untuk semua ikon aksi. `null` hanya untuk ikon dekoratif yang sudah ada label teksnya.
- Error jaringan tidak membuang data yang sudah ditampilkan. Retry idempoten dengan `client_request_id`.
- Jangan tampilkan keberhasilan sebelum konfirmasi dari server.

## Struktur file yang disarankan (lihat ARCHITECTURE.md §3 & §13)

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
  feature/scan/        ★ ScanBottomSheet.kt, ScanViewModel.kt (AI)
  feature/statistics/  Statistics*
  feature/profile/     Profile*, Achievement*, Settings*
  data/network/        WasteNotApi.kt, dto/, AuthInterceptor.kt
  data/repository/     AuthRepository.kt, LogRepository.kt, ...
  data/ai/             ★ AiRepository.kt, dto/ (AI detections, recommendations)
  data/local/          PreferencesStore.kt, DraftStore.kt, TokenStore.kt
  core/camera/         ★ CameraManager.kt, ImageCaptureHelper.kt (AI)
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
- Jangan auto-save waste log tanpa konfirmasi user eksplisit.
- Jangan simpan foto scan lebih dari 1 jam tanpa persetujuan user.
- Jangan rekomendasikan REDUCE dari hasil scan AI.
