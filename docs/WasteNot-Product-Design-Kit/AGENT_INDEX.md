# AGENT_INDEX.md — WasteNot Design Kit Quick Index

Indeks cepat untuk AI agent. Gunakan ini sebagai peta untuk menemukan informasi spesifik tanpa harus baca seluruh dokumen.

## Peta dokumen

```
CLAUDE.md (root)          ← Baca ini dulu. Konteks proyek + aturan utama.
AGENT.md (root)           ← Quick reference, task-based reading guide.
docs/WasteNot-Product-Design-Kit/
  ├── README.md            ← Ikhtisar kit, cara pakai design-system.html
  ├── PRD.md               ← Domain rules, scope, metrik
  ├── DESIGN.md            ← Token warna, font, spacing, komponen, aksesibilitas
  ├── SCREEN_SPEC.md       ← Detail tiap layar dan komponen
  ├── ARCHITECTURE.md      ← Struktur Android, navigasi, state, security
  ├── API.md               ← Data model, enum, endpoint, DTO
  ├── IMPLEMENTATION_PLAN.md ← Backlog & acceptance test
  ├── QA.md                ← QA HTML prototype
  ├── design-system.html   ← Mockup visual interaktif (buka di browser)
  └── AGENT_INDEX.md       ← Dokumen ini
```

---

## Cari informasi berdasarkan topik

### Warna & tema
- Token semantik (primary, surface, error, dll.) → [`DESIGN.md §2`](DESIGN.md#2-warna-semantik)
- Gradasi pine–lime dan efek kaca navigasi → [`DESIGN.md §2 Material v1.1`](DESIGN.md#material-v11-gradasi-dan-kaca)
- Handoff Compose (`WasteNotTheme`, `ColorScheme`, `dynamic color`) → [`DESIGN.md §10`](DESIGN.md#10-handoff-compose-dan-cakupan-prototype)

### Tipografi
- Semua role typography (displayLarge hingga labelSmall) → [`DESIGN.md §3`](DESIGN.md#3-tipografi-dan-font)
- Setup font di Compose (`FontFamily`, resource naming) → [`DESIGN.md §3 Font di Compose`](DESIGN.md#font-di-compose)
- File font: `assets/fonts/PlusJakartaSans-Variable.ttf` + lisensi `OFL-PlusJakartaSans.txt`

### Spacing, ukuran, bentuk
- Token spacing (space1–space12 dalam dp) → [`DESIGN.md §4`](DESIGN.md#4-spacing-ukuran-bentuk-dan-elevasi)
- Radius, touch target, elevasi → [`DESIGN.md §4`](DESIGN.md#4-spacing-ukuran-bentuk-dan-elevasi)

### Layout & breakpoint
- Pola satu kolom / NavigationRail / list-detail → [`DESIGN.md §5`](DESIGN.md#5-layout-dan-hierarki-layar)
- Breakpoint: <600 dp, 600–839 dp, ≥840 dp → [`DESIGN.md §5`](DESIGN.md#5-layout-dan-hierarki-layar)
- Safe area, insets, keyboard → [`ARCHITECTURE.md §7`](ARCHITECTURE.md#7-theme-layout-dan-aksesibilitas-compose)

### Komponen UI (C01–C34)
- Daftar lengkap semua komponen → [`SCREEN_SPEC.md §4`](SCREEN_SPEC.md)
- Aturan visual per kelompok komponen → [`DESIGN.md §6`](DESIGN.md#6-spesifikasi-komponen)
- Compose equivalents (OutlinedTextField, SegmentedButton, dll.) → [`DESIGN.md §6`](DESIGN.md#6-spesifikasi-komponen)

### Layar (S01–S20)
- Inventaris semua layar + prioritas + route → [`SCREEN_SPEC.md §2`](SCREEN_SPEC.md#2-inventaris-layar)
- Detail field, state, dan aksi per layar → [`SCREEN_SPEC.md §3`](SCREEN_SPEC.md#3-detail-per-layar)
- Layar auth: S01 (session), S02 (login), S03 (register) → [`SCREEN_SPEC.md §3`](SCREEN_SPEC.md)
- Layar utama: S04 (home), S07 (history), S11 (guide), S13 (statistics), S14 (profile) → [`SCREEN_SPEC.md §3`](SCREEN_SPEC.md)
- Editor log: S05 (create), S09 (edit) → [`SCREEN_SPEC.md §3`](SCREEN_SPEC.md)

### Domain & metrik
- Definisi Reduce, Reuse, Recycle, Trash → [`PRD.md §6`](PRD.md)
- Aturan recycling rate (null vs 0, basis perhitungan) → [`PRD.md §9`](PRD.md)
- XP per log, deduction saat delete, level → [`PRD.md §8`](PRD.md)
- Dataset referensi: 5 log, 1.400 g, rate 50%, 50 XP, streak 2 → [`PRD.md §9.4`](PRD.md)
- Acceptance test lengkap (AT-01 hingga AT-26) → [`IMPLEMENTATION_PLAN.md §3`](IMPLEMENTATION_PLAN.md#3-kasus-uji-penerimaan-utama)

### API & network
- Semua enum (`category`, `method`, `quantity_kind`) → [`API.md §2.2`](API.md#22-enum)
- Data model entities (users, sessions, waste_logs, dll.) → [`API.md §2.1`](API.md#21-entitas)
- Endpoint auth (register, login, refresh, logout) → [`API.md §3`](API.md)
- Endpoint waste log (create, list, detail, update, delete) → [`API.md §4`](API.md)
- Idempoten dengan `client_request_id` → [`API.md §4`](API.md) dan [`PRD.md §7`](PRD.md)
- Konvensi response format (`data`, `page`, `error`) → [`API.md §1`](API.md#1-konvensi)

### Navigasi Android
- Daftar semua route + argumen → [`ARCHITECTURE.md §6`](ARCHITECTURE.md#6-navigasi)
- Perilaku back, dirty form, tab reselect → [`ARCHITECTURE.md §6`](ARCHITECTURE.md#6-navigasi)
- Navigation 3 setup (sudah terpasang) → [`ARCHITECTURE.md §1`](ARCHITECTURE.md#1-keputusan-dasar)

### State & arsitektur
- UDF flow (event → ViewModel → Repository → state → UI) → [`ARCHITECTURE.md §4`](ARCHITECTURE.md#4-alur-state-dan-event)
- State layout daftar, state editor → [`ARCHITECTURE.md §4`](ARCHITECTURE.md#4-alur-state-dan-event)
- Pemulihan state rotasi, process death, back ke background → [`ARCHITECTURE.md §5`](ARCHITECTURE.md#5-pemulihan-state-dan-koneksi)

### Security
- Token storage (Keystore, bukan DataStore) → [`ARCHITECTURE.md §8`](ARCHITECTURE.md#8-penyimpanan-credential-dan-jaringan)
- Refresh serialization, retry rules → [`ARCHITECTURE.md §8`](ARCHITECTURE.md#8-penyimpanan-credential-dan-jaringan)
- Logging interceptor (redact Authorization/password) → [`ARCHITECTURE.md §8`](ARCHITECTURE.md#8-penyimpanan-credential-dan-jaringan)

### Aksesibilitas
- Target sentuh, fokus, label → [`DESIGN.md §9`](DESIGN.md#9-aksesibilitas-dan-resilience)
- Chart tanpa dependensi warna → [`DESIGN.md §7`](DESIGN.md#7-ikon-imagery-dan-visualisasi)
- Font scale 200%, reduced motion → [`DESIGN.md §9`](DESIGN.md#9-aksesibilitas-dan-resilience)

### Microcopy & state text
- Semua contoh teks state (empty, loading, error, success, hapus) → [`DESIGN.md §8`](DESIGN.md#8-state-microcopy-dan-motion)
- Bahasa yang harus dihindari (jangan "CO₂ dihemat", jangan menghakimi Trash) → [`DESIGN.md §8`](DESIGN.md#8-state-microcopy-dan-motion)

### Scope & priority MVP
- Fitur must/should/stretch → [`PRD.md §5`](PRD.md#5-cakupan-dan-prioritas)
- Gate per minggu → [`IMPLEMENTATION_PLAN.md §2`](IMPLEMENTATION_PLAN.md#2-backlog-per-minggu)
- Fitur opsi: foto, reminder, recycling point → [`ARCHITECTURE.md §11`](ARCHITECTURE.md#11-fitur-opsional-android)

---

## Apa yang tidak ada di design kit ini

- Backend implementation (Go, PostgreSQL) → hanya ada kontrak di `API.md`
- APK atau Android project yang sudah di-build → lihat root repo
- Credential, token, atau data pengguna nyata
- Library chart, map, atau analytics yang sudah dipilih
- Keputusan tentang hosting, CI/CD, atau app signing

---

*Tanggal paket: PRD & ARCHITECTURE 21 Sep 2026, DESIGN 23 Sep 2026. Verifikasi versi dependency di [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml) sebelum memulai implementasi.*
