# Panduan Setup Plugin Development — WasteNot Android

Dokumen ini berisi panduan lengkap mengenai plugin development yang telah diintegrasikan ke dalam proyek **WasteNot Android**, termasuk cara kerja, instalasi plugin di Android Studio, cara penggunaan harian, serta perintah (commands/shortcuts) yang tersedia.

---

## Daftar Isi
1. [Ikhtisar Plugin](#1-ikhtisar-plugin)
2. [Compose HotSwan v2 (Hot Reload)](#2-compose-hotswan-v2-hot-reload)
3. [Compose Navigation Graph (Visualisasi Navigasi)](#3-compose-navigation-graph-visualisasi-navigasi)
4. [Plugin Android Studio Rekomendasi (ADB Idea & JSON to Kotlin)](#4-plugin-android-studio-rekomendasi)
5. [Tabel Cheatsheet Shortcut & Perintah](#5-tabel-cheatsheet-shortcut--perintah)

---

## 1. Ikhtisar Plugin

Untuk mempercepat siklus development aplikasi berbasis **Jetpack Compose + Navigation 3**, proyek ini telah dilengkapi dengan toolchain modern:

| Komponen / Plugin | Tipe | Versi | Pembuat | Fungsi Utama |
|---|---|---|---|---|
| **Compose HotSwan v2** | Gradle Compiler Plugin + IDE Plugin | `2.0.1` | Jaewoong Eum (skydoves) | Hot-reload UI Compose secara instan (milidetik) tanpa re-run aplikasi & mempertahankan state input/navigasi. |
| **Compose Navigation Graph** | Gradle Plugin + KSP Processor + IDE Plugin | `0.3.0` | Jaewoong Eum (skydoves) | Visualisasi arsitektur navigasi Compose sebagai graph interaktif dengan preview thumbnail layar. |
| **KSP (Kotlin Symbol Processing)** | Gradle Plugin | `2.3.12` | Google | Engine annotation processing untuk NavGraph. |
| **ADB Idea** | IDE Plugin | Latest | Philippe Breault | Akses cepat kontrol aplikasi di device (Clear data, Restart, Kill, Uninstall). |
| **JSON To Kotlin Class** | IDE Plugin | Latest | Wu Xianlin | Generate otomatis `data class` Kotlin bertipe `@Serializable` dari JSON response backend. |

---

## 2. Compose HotSwan v2 (Hot Reload)

### 2.1 Apa itu HotSwan?
Compose HotSwan v2 adalah solusi hot-reload berbasis interpreter compiler untuk Jetpack Compose di Android. Berbeda dengan Live Edit bawaan yang terbatas, HotSwan melakukan swap class secara langsung di ART (Android Runtime), sehingga:
- State form input, remembered value, dan back-stack navigasi **tidak hilang/reset**.
- Mendukung perubahan fungsi Composable, fungsi reguler, dan resources.

### 2.2 Konfigurasi Gradle (Sudah Terpasang di Proyek)
Jika Anda perlu mereferensikan kembali bagaimana plugin ini dikonfigurasi di file build:

1. **`gradle/libs.versions.toml`**:
   ```toml
   [versions]
   hotswan = "2.0.1"

   [plugins]
   hotswan-compiler = { id = "com.github.skydoves.compose.hotswan.compiler", version.ref = "hotswan" }
   ```

2. **Root `build.gradle.kts`**:
   ```kotlin
   plugins {
       alias(libs.plugins.hotswan.compiler) apply false
   }
   ```

3. **`app/build.gradle.kts`**:
   ```kotlin
   plugins {
       alias(libs.plugins.hotswan.compiler) apply false
   }
   ```

Plugin hanya diterapkan ketika properti `wastenot.hotReload=true`. Build biasa memakai native; aktifkan opsi tersebut melalui Gradle Run Configuration untuk sesi hot reload.

> **Catatan Penting**: Versi Gradle plugin HotSwan **harus sama persis** dengan versi IDE Plugin di Android Studio (saat ini menggunakan `2.0.1`).

### 2.3 Instalasi IDE Plugin di Android Studio
1. Buka Android Studio.
2. Masuk ke **Settings / Preferences** (`Ctrl + Alt + S` di Linux/Windows, `Cmd + ,` di macOS).
3. Pilih menu **Plugins** > tab **Marketplace**.
4. Ketik **"Compose HotSwan"** di kolom pencarian.
5. Klik **Install**, lalu klik **Restart IDE** setelah instalasi selesai.

### 2.4 Cara Penggunaan Harian
1. Aktifkan `-Pwastenot.hotReload=true` untuk sesi pengembangan, lalu jalankan aplikasi ke emulator atau device fisik dengan tombol **Run** (`Shift + F10`).
2. Buka tool window HotSwan: **View** > **Tool Windows** > **HotSwan** (biasanya ada di bilah kanan atau bawah IDE).
3. Periksa status pada tool window:
   - Jika status berwarna hijau dan bertuliskan **`WATCHING`**, koneksi hot-reload telah aktif.
4. Lakukan modifikasi kode Composable (misalnya ubah warna, teks, padding, atau susunan layout).
5. Tekan **Save** (`Ctrl + S` di Linux/Windows atau `Cmd + S` di macOS).
6. Perubahan akan langsung terlihat di layar device dalam hitungan milidetik tanpa merestart aplikasi.

### 2.5 Troubleshooting HotSwan

Checkpoint DataStore 9 November menemukan bahwa reflection HotSwan 2.0.1 membungkus IOException dari pembacaan suspend sebelum catch ThemeViewModel. Pemulihan baca pada mode interpreter gagal di unit test; kombinasi dua pola pengecualian juga gagal pada parser opsi Kotlin. Build native kini default dan pemulihan I/O diterima melalui unit/native Android. Jalur hot reload eksplisit tetap untuk pengembangan tampilan; pakai native saat memeriksa perilaku data/pemulihan. [Bukti dan batasnya](archive/qa/iteration-2/THEME_DATASTORE.md#build-native-sebagai-default).

`SessionTokens` dikecualikan secara spesifik melalui `hotSwanCompiler.exclude` di app Gradle. HotSwan 2.0.1 gagal membuat dispatch bridge `component2` untuk `RefreshToken` value class lintas file (`B3: ... has no primary constructor`). Dengan pengecualian DTO tersebut, build kembali lulus dan tipe credential tetap tervalidasi. Perubahan pada DTO ini memerlukan rebuild; hot reload UI tetap tersedia. Opsi pengecualian mengikuti [konfigurasi resmi HotSwan](https://hotswan.dev/docs/gradle-configuration#excluding-classes).

- **Status tidak berubah ke WATCHING**: Pastikan path modul aplikasi diatur ke `:app` melalui **Settings** > **Tools** > **Compose HotSwan** > **App Module Path** (default: `:app`).
- **Method yang di-skip**: Method lifecycle yang memanggil `super.<method>()` seperti `MainActivity.onCreate()` sengaja dilewati oleh interpreter dan tetap berjalan secara native. Modifikasi di luar itu (semua Composable functions) di-reload secara penuh.

---

## 3. Compose Navigation Graph (Visualisasi Navigasi)

### 3.1 Apa itu Compose Navigation Graph?
Tool untuk memetakan dan memvisualisasikan seluruh alur navigasi Jetpack Compose (kompatibel penuh dengan **Navigation 3**) dalam bentuk diagram alur grafis interaktif langsung di dalam Android Studio.

### 3.2 Konfigurasi Gradle (Sudah Terpasang di Proyek)
1. **`gradle/libs.versions.toml`**:
   ```toml
   [versions]
   ksp = "2.3.12"
   skydovesNavgraph = "0.3.0"

   [plugins]
   ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
   skydoves-navgraph = { id = "com.github.skydoves.navgraph", version.ref = "skydovesNavgraph" }
   ```

2. **Root `build.gradle.kts`**:
   ```kotlin
   plugins {
       alias(libs.plugins.ksp) apply false
       alias(libs.plugins.skydoves.navgraph) apply false
   }
   ```

3. **`app/build.gradle.kts`**:
   ```kotlin
   plugins {
       alias(libs.plugins.ksp)
       alias(libs.plugins.skydoves.navgraph)
   }
   ```

### 3.3 Instalasi IDE Plugin di Android Studio
1. Buka **Settings / Preferences** > **Plugins** > **Marketplace**.
2. Cari **"Compose Navigation Graph"**.
3. Klik **Install**, lalu restart Android Studio.
4. Buka tool window melalui menu **View** > **Tool Windows** > **NavGraph Graph**.

### 3.4 Cara Penggunaan di Kode (Anotasi)
Tambahkan anotasi dari package `com.github.skydoves.navgraph.annotations.*`:

1. **Anotasi Root Navigasi (`@NavGraphRoot`)**:
   Diletakkan pada Composable yang membungkus `NavDisplay` / `rememberNavBackStack`:
   ```kotlin
   // File: app/src/main/java/com/example/wastenot/Navigation.kt
   import com.github.skydoves.navgraph.annotations.NavGraphRoot

   @NavGraphRoot
   @Composable
   fun MainNavigation() {
       val backStack = rememberNavBackStack(Main)
       NavDisplay(...)
   }
   ```

2. **Anotasi Layar Tujuan (`@NavDestination`)**:
   Diletakkan pada Composable layar dengan parameter route bertipe `KClass<*>`:
   ```kotlin
   // File: app/src/main/java/com/example/wastenot/ui/main/MainScreen.kt
   import com.github.skydoves.navgraph.annotations.NavDestination

   @NavDestination(route = Main::class)
   @Composable
   fun MainScreen(...) { ... }
   ```

3. **Anotasi Thumbnail Preview (`@NavPreview`)**:
   Diletakkan pada Composable `@Preview` agar thumbnail tampilan layar dirender dan ditampilkan pada diagram grafis:
   ```kotlin
   // File: app/src/main/java/com/example/wastenot/ui/main/MainScreen.kt
   import com.github.skydoves.navgraph.annotations.NavPreview

   @NavPreview(route = Main::class)
   @Preview(showBackground = true)
   @Composable
   fun MainScreenPreview() {
       WasteNotTheme { MainScreen(...) }
   }
   ```

### 3.5 Perintah & Ekspor Graph

Anda dapat menjalankan perintah melalui terminal atau menggunakan target `Makefile`:

| Aksi | Perintah Makefile | Perintah Gradle | Output File |
|---|---|---|---|
| **Generate Data Graph** | `make nav-graph` | `./gradlew :app:generateNavGraph` | `app/build/navgraph/nav-graph.json` |
| **Ekspor Web HTML Interaktif** | `make nav-html` | `./gradlew :app:exportNavGraphHtml` | `app/build/navgraph/nav-graph.html` |
| **Ekspor Diagram Mermaid (Markdown)** | `make nav-mermaid` | `./gradlew :app:exportNavGraphMermaid` | `app/build/navgraph/nav-graph.mmd` |
| **Linter Navigasi (Cek Route Rusak)** | `make nav-lint` | `./gradlew :app:navLint` | Laporan terminal |
| **Simpan Baseline `.nav`** | `make nav-dump` | `./gradlew :app:navDump` | File baseline commit |
| **Validasi CI vs Baseline** | `make nav-check` | `./gradlew :app:navCheck` | Exit code 0 jika valid |

> **Tips Tim**: File HTML (`app/build/navgraph/nav-graph.html`) dapat langsung dibuka di browser web manapun tanpa perlu menjalankan server atau emulator.

---

## 4. Plugin Android Studio Rekomendasi

Untuk kenyamanan development harian, dua plugin berikut sangat disarankan di-install oleh setiap developer di tim:

### 4.1 ADB Idea
*   **Fungsi**: Melakukan operasi umum debugging Android tanpa harus mengetik perintah adb manual di terminal.
*   **Cara Install**: Di Marketplace plugin Android Studio, cari **ADB Idea**.
*   **Shortcut**:
    - **Linux / Windows**: `Ctrl + Alt + Shift + A`
    - **macOS**: `Cmd + Shift + A` (atau cari menu *Tools > ADB Idea*)
*   **Aksi Cepat yang Sering Dipakai**:
    - **ADB Clear App Data**: Reset database/DataStore tanpa uninstall (berguna saat test auth logout).
    - **ADB Clear App Data and Restart**: Reset data lalu buka aplikasi kembali secara otomatis.
    - **ADB Restart App**: Restart proses aplikasi secara instan.
    - **ADB Uninstall App**: Hapus aplikasi dari emulator/device.

### 4.2 JSON To Kotlin Class (JsonToKotlinClass)
*   **Fungsi**: Mengonversi JSON payload dari API dokumentasi ([API.md](file:///home/oktaavsm/Code/github.com/wastenot-android/docs/design-kit/API.md)) menjadi Kotlin `data class` lengkap dengan anotasi `@Serializable`.
*   **Cara Install**: Di Marketplace plugin Android Studio, cari **JSON To Kotlin Class**.
*   **Shortcut**:
    - Di dalam folder source code Kotlin / package DTO, tekan `Alt + K`.
    - Paste JSON response dari backend/dokumentasi, beri nama class, pilih parser *KotlinX Serialization*, lalu klik **Generate**.

---

## 5. Tabel Cheatsheet Shortcut & Perintah

### Shortcut Keyboard IDE (Android Studio)
| Fitur / Plugin | Linux / Windows | macOS | Fungsi |
|---|---|---|---|
| **HotSwan Hot Reload** | `Ctrl + S` | `Cmd + S` | Memicu hot-reload instan pada file yang sedang diedit |
| **ADB Idea Menu** | `Ctrl + Alt + Shift + A` | `Cmd + Shift + A` | Menu aksi cepat: Clear Data, Restart, Kill, Uninstall |
| **JSON to Kotlin Data Class** | `Alt + K` | `Option + K` | Konversi payload JSON clipboard ke data class |
| **Reformat Code** | `Ctrl + Alt + L` | `Cmd + Option + L` | Format kode sesuai panduan Kotlin |
| **Run App** | `Shift + F10` | `Control + R` | Build dan jalankan aplikasi ke device/emulator |

### Perintah Terminal / Makefile
```bash
# Build & Jalankan Aplikasi
make build                  # Build APK debug
make run                    # Build, install, dan launch aplikasi di device
make clean                  # Bersihkan folder build

# Validasi Kualitas Kode
make check                  # Jalankan Android Lint + Unit Tests
make pr-ready               # Cek menyeluruh (lint + test + nav-check) sebelum buat PR

# Navigasi & Graph Visualizer
make nav-graph              # Ekstraksi graph navigasi ke JSON
make nav-html               # Ekspor graph ke HTML interaktif dan otomatis buka di browser
make nav-mermaid            # Ekspor representasi diagram Mermaid
make nav-lint               # Cek apakah ada route yang tidak terjangkau (unreachable)

# Branching Otomatis Sesuai Tiket Sprint
make branch-feature TICKET=W1-03 NAME=theme-setup   # Buat branch feature dari develop
make branch-fix     TICKET=W2-04 NAME=conflict-409  # Buat branch fix dari develop
make branch-hotfix  TICKET=WN-010 NAME=crash-auth   # Buat branch hotfix dari main
```
