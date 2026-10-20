# WasteNot Android

WasteNot membantu pengguna mencatat aktivitas Reduce, Reuse, Recycle dan Trash, mencari panduan, serta memantau kebiasaan pribadi.

Prototipe lokal **YOLO + Gemini**, katalog objek pilot, baseline inference, pengujian policy dan worker privat tersedia di [panduan AI](ai/README.md). Panduan tersebut mencakup cara mencoba YOLO/LLM, konfigurasi Gemini, dan langkah integrasi backend Go serta frontend Android. Integrasi AI ke aplikasi masih mengikuti backlog; mode fixture dan panduan draf diberi label khusus.

## Menggunakan YOLO dan Gemini

**Demo kamera interaktif:** setelah environment AI siap, jalankan dari root repo:

```powershell
.\ai\demo-camera.bat
```

Browser terbuka di `http://127.0.0.1:8092` dan meminta izin kamera. Izinkan kamera → ambil foto → tinjau dan kirim untuk deteksi → klik bbox/daftar objek → konfirmasi material serta kondisi → minta rekomendasi Gemini. Tombol **Pilih foto** dan katalog manual tersedia jika kamera tidak bisa digunakan. Hentikan server dengan `Ctrl+C` pada terminal.

Gemini memilih **satu** label **Reduce, Reuse, Recycle, atau Trash**, disertai alasan, langkah dan tautan sumber panduan. Reduce ditandai sebagai pencegahan penggunaan berikutnya. **Penanganan khusus menjadi peringatan tambahan**, bukan pengganti label: kaca pecah → Trash dengan pengemasan pelindung; baterai → Recycle melalui pengelola khusus. Contoh keempat label dan kontrak backend/frontend ada di [kontrak rekomendasi](ai/docs/RECOMMENDATION_CONTRACT.md). Saat layanan/panduan tidak tersedia, demo menampilkan kegagalan dengan jelas.

Demo memakai katalog 68 pilihan benda/material yang terhubung ke 58 kelas visual checkpoint COCO; jenis yang belum memiliki kelas, misalnya kaleng/kardus/baterai, tetap dipilih manual. Semua panduan perlu review lokal; launcher ini mengaktifkan mode draf berlabel khusus demonstrasi. Langkah, cakupan dan batasnya ada di [demo kamera](ai/README.md#demo-kamera-di-browser).

Semua perintah berikut dijalankan dari root repository melalui PowerShell. Prototipe Python dapat dicoba tanpa menjalankan Android atau backend Go.

```powershell
python -m venv .venv-ai
.\.venv-ai\Scripts\python.exe -m pip install -e "./ai[yolo,worker,test]" -c ai/constraints.txt
# Salin konfigurasi contoh hanya jika file lokal belum ada.
if (-not (Test-Path -LiteralPath ai/.env)) { Copy-Item -LiteralPath ai/.env.example -Destination ai/.env }
.\.venv-ai\Scripts\python.exe -m wastenot_ai.cli fetch-assets
.\.venv-ai\Scripts\python.exe -m wastenot_ai.cli doctor
```

Untuk Gemini sungguhan, isi `GEMINI_API_KEY` dan `GEMINI_MODEL` pada `ai/.env` dengan kredensial/model yang tersedia di akunmu. File lokal tersebut diabaikan Git. `--demo-guides` pada contoh di bawah mengizinkan panduan draf khusus demonstrasi; tanpa flag itu, panduan bawaan belum dapat dipakai untuk rekomendasi produksi.

Untuk rotasi lima key, isi `GEMINI_API_KEY_1` sampai `GEMINI_API_KEY_5` di `ai/.env`, lalu mulai ulang demo. Slot kosong/duplikat dilewati; key dipakai bergiliran dan diistirahatkan saat HTTP 429 sebelum mencoba key lain yang tersedia. Jika semua slot bernomor kosong, key tunggal lama tetap digunakan. Periksa jumlah key dengan `doctor`. [Panduan rotasi dan cooldown](ai/README.md#gemini-api) menjelaskan batas percobaan dan integrasi backend. Google menerapkan [kuota per proyek](https://ai.google.dev/gemini-api/docs/rate-limits), sehingga banyak key pada satu proyek tetap berbagi limit.

```powershell
# Coba YOLO saja; tidak membutuhkan Gemini API key.
.\.venv-ai\Scripts\python.exe -m wastenot_ai.cli detect ai/data/local/smoke/bottle.jpg --output ai/artifacts/detection.json

# Coba Gemini saja dengan konteks contoh; membutuhkan API key.
.\.venv-ai\Scripts\python.exe -m wastenot_ai.cli recommend --context ai/examples/context-glass.json --provider gemini --demo-guides --output ai/artifacts/gemini-demo.json

# Coba alur rekomendasi offline dengan simulator yang diberi label fixture.
.\.venv-ai\Scripts\python.exe -m wastenot_ai.cli recommend --context ai/examples/context-glass.json --provider fixture --demo-guides
```

Ganti path foto untuk memakai gambarmu sendiri. Ubah context JSON mengikuti material dan kondisi yang dikonfirmasi pengguna. CLI menampilkan hasil rekomendasi untuk ditinjau; penyimpanan aktivitas tetap melalui backend setelah konfirmasi metode dan input jumlah.

| Kebutuhan | Panduan |
|---|---|
| Instalasi, objek pilot dan batas model | [Mulai](ai/README.md#mulai-di-powershell), [objek pilot](ai/README.md#objek-pilot-awal) |
| Mencoba YOLO + Gemini dalam satu alur | [Percobaan langkah demi langkah](ai/README.md#percobaan-langkah-demi-langkah) |
| API key, model dan output Gemini | [Konfigurasi Gemini](ai/README.md#gemini-api) |
| Menjalankan worker dan menghubungkannya ke Go | [Integrasi backend](ai/README.md#integrasi-backend-go) |
| Endpoint, DTO dan alur scan Android | [Integrasi frontend](ai/README.md#integrasi-frontend-android) |
| Error dan pemeriksaan hasil | [Troubleshooting](ai/README.md#troubleshooting) |
| Pembagian tanggung jawab dan pekerjaan integrasi tersisa | [Handoff](ai/docs/HANDOFF.md) |

Worker Python untuk deteksi sudah tersedia. Endpoint AI publik, sesi/idempotensi dan pemanggilan Gemini produksi masih perlu diimplementasikan pada Go; frontend kemudian berkomunikasi melalui Go. Gemini key dan token worker hanya berada pada lingkungan server.

## Status implementasi

Repo memiliki satu module `:app`, Navigation 3, tema terang/gelap, form bersama, transport HTTP dan penyimpanan sesi berbasis Android Keystore. Startup, Masuk/Daftar, lima tab, editor manual dan logout terhubung ke alur sesi. Pilihan **Lihat data contoh** membuka dataset berlabel untuk mencoba pencatatan, edit dan statistik lokal. Integrasi write/ringkasan backend nyata, konten panduan dan AI masih mengikuti backlog; adapter produksi tidak memalsukan simpan berhasil. Mockup HTML menjadi referensi desain. Bukti implementasi dan batas pengujian ada di [QA Iteration 1](docs/tasks/ITERATION_1_QA.md).

- [Roadmap dan seluruh tugas](https://github.com/waste-not-app/waste-not-android/issues/2)
- [Kanban iterasi berjalan](https://github.com/orgs/waste-not-app/projects/1/views/1)
- [Setup Android dan konfigurasi lingkungan](docs/ANDROID_SETUP.md)
- [Catatan perubahan, pengujian, dan checklist per task](docs/tasks/README.md)

## Toolchain

Kotlin 2.3.20, AGP 9.0.1, Gradle 9.1.0, JDK 17, Compose BOM 2026.03.01, Material 3 dan Navigation 3. Min SDK 24; compile/target SDK 36. Versi tersimpan di [version catalog](gradle/libs.versions.toml).

## Build dan pemeriksaan

Siapkan JDK 17, Android SDK Platform 36, serta `ANDROID_HOME` atau `sdk.dir` dalam `local.properties` lokal.

```sh
./gradlew lintDebug lintStaging lintRelease testDebugUnitTest
./gradlew assembleDebug assembleStaging assembleRelease
./gradlew assembleDebugAndroidTest
python3 scripts/verify_build_environments.py
python3 scripts/verify_release_workflow.py
```

APK tersedia di `app/build/outputs/apk/{debug,staging,release}/`. Release tanpa konfigurasi signing merupakan APK unsigned. Staging/release belum memiliki alamat backend default; URL HTTPS disediakan sebagai property Gradle sesuai [panduan setup](docs/ANDROID_SETUP.md).

## Alur kontribusi

Buat branch tugas `codex/<ticket>-<nama>` dari commit dasar yang sudah diverifikasi. Remote saat audit memiliki `main`; checkout terpisah dapat dipakai untuk menjaga perubahan lokal lainnya. Commit hanya file terkait tugas setelah pemeriksaan lulus, lalu ajukan PR untuk review. Setiap perubahan kode harus menyebut issue terkait; merge ke main menjalankan pipeline rilis yang sudah ada.

[Android CI](.github/workflows/android-ci.yml) menjalankan lint, unit test, pemeriksaan konfigurasi, build tiga lingkungan dan kompilasi APK instrumentasi pada branch pengembangan, termasuk `codex/**`. Tes perangkat Keystore dijalankan lokal dengan langkah pada [catatan A04](docs/tasks/A04-session.md). Workflow [release/deploy](.github/workflows/android-release.yml) terpisah dan membutuhkan konfigurasi signing serta secrets deployment. Panduannya ada di [CICD_SETUP.md](docs/CICD_SETUP.md).

## Referensi

| Dokumen | Isi |
|---|---|
| [Design kit terbaru](<docs/WasteNot-Product-Design-Kit update/WasteNot/README.md>) | Indeks PRD, arsitektur, API, desain dan mockup S01–S25 |
| [PRD](<docs/WasteNot-Product-Design-Kit update/WasteNot/PRD.md>) | Scope, aturan unit/metrik dan acceptance |
| [Arsitektur](<docs/WasteNot-Product-Design-Kit update/WasteNot/ARCHITECTURE.md>) | State, repository, navigasi dan infrastruktur |
| [Design system](<docs/WasteNot-Product-Design-Kit update/WasteNot/design-system.html>) | Referensi visual interaktif dengan data simulasi |
| [AI specification](<docs/WasteNot-Product-Design-Kit update/WasteNot/AI_SPEC.md>) | Capture, konfirmasi, rekomendasi dan manual fallback |
| [Developer plugins](docs/DEV_PLUGINS.md) | HotSwan, KSP dan navigation graph |
| [AGENT.md](AGENT.md), [CLAUDE.md](CLAUDE.md) | Petunjuk implementasi untuk agent |

Perintah tambahan tersedia melalui `make help`. Tooling plugin yang sudah ada dipertahankan; backend/API nyata dan pembagian pekerjaan mengikuti roadmap.
