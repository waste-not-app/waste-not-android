# AGENT_INDEX.md: WasteNot Design Kit Quick Index

Indeks cepat untuk AI coding agent. Gunakan ini sebagai peta untuk menemukan informasi spesifik tanpa harus baca seluruh dokumen.

> **Versi kit ini: v1.3 · 28 September 2026**, Core + AI extension (YOLO + LLM recommendation pipeline, S21–S25, C35–C39)

## Peta dokumen

```
CLAUDE.md (root)          ← Baca ini dulu. Konteks proyek + aturan utama.
AGENT.md (root)           ← Quick reference, task-based reading guide.
docs/design-kit/
  ├── README.md            ← Ikhtisar kit v1.3 dan cara pakai design-system.html
  ├── AI_SPEC.md           ← ★ BARU, Spesifikasi penuh AI pipeline (YOLO/Guide/LLM)
  ├── PRD.md               ← Domain rules, scope, metrik, AI increment v1.2
  ├── DESIGN.md            ← Design system + komponen AI (C35–C39) v1.3
  ├── SCREEN_SPEC.md       ← Layar + komponen termasuk S21–S25 AI scan v1.3
  ├── ARCHITECTURE.md      ← Arsitektur Android + AI extension §13 v1.2
  ├── API.md               ← Kontrak API core + AI endpoints §10 v1.2
  ├── IMPLEMENTATION_PLAN.md ← Backlog 4 minggu + AI increment stages §10 v1.2
  ├── QA.md                ← QA HTML prototype
  ├── design-system.html   ← Mockup visual interaktif incl. AI scan flow (buka di browser)
  ├── ai-demo.js           ← ★ BARU, Script demo AI untuk HTML prototype
  ├── verify.cjs           ← Automated test inti
  ├── verify-ai.cjs        ← ★ BARU, Automated test khusus AI scan flow
  ├── assets/
  │   ├── preview-ai.png   ← ★ BARU, Preview layar AI scan
  │   ├── qa-core-v1.2/1.3.json ← ★ BARU, QA core hasil versioned
  │   └── qa-ai-v1.2/1.3.json  ← ★ BARU, QA AI hasil versioned
  ├── sources/
  │   └── AI_PLAN_ORIGINAL.md ← ★ BARU, Dokumen source plan tim (referensi, bukan spec final)
  └── AGENT_INDEX.md       ← Dokumen ini
```

---

## Cari informasi berdasarkan topik

### ★ AI Pipeline (BARU: v1.2/1.3)
- Overview pipeline (Camera → YOLO → Mapping → Guide → LLM → User confirm) → [`AI_SPEC.md §1–2`](AI_SPEC.md)
- Kamera & siklus hidup CameraX (izin, capture, cancel, EXIF, process death) → [`AI_SPEC.md §3`](AI_SPEC.md)
- Confidence bands (≥0.70 OFFERED / 0.50–0.69 UNCERTAIN / <0.50 MANUAL_REQUIRED) → [`AI_SPEC.md §4`](AI_SPEC.md)
- Waste mapping (label YOLO → kategori WasteNot + guide_key + special_handling) → [`AI_SPEC.md §4`](AI_SPEC.md)
- Context kondisi objek (cleanliness, integrity, reusable, reuse_intention) → [`AI_SPEC.md §5`](AI_SPEC.md)
- LLM: apa yang dikirim, output JSON, validasi server, error UNAVAILABLE → [`AI_SPEC.md §6`](AI_SPEC.md)
- State machine kamera (Idle → Permission → Preview → ReviewPhoto → Detecting → ...) → [`AI_SPEC.md §7`](AI_SPEC.md)
- Privacy & retensi foto (ephemeral, TTL, cleanup, opt-in training) → [`AI_SPEC.md §8`](AI_SPEC.md)
- Release gate AI (taxonomy, model, mapping, holdout target, AT-AI semua lulus) → [`AI_SPEC.md §9`](AI_SPEC.md)
- Sumber original rencana tim → [`sources/AI_PLAN_ORIGINAL.md`](sources/AI_PLAN_ORIGINAL.md)

### ★ Layar AI Scan (S21–S25, BARU)
- S21: Camera & scan bottom sheet (preview, capture, scan, retake, manual fallback) → [`SCREEN_SPEC.md §3.21`](SCREEN_SPEC.md)
- S22: Konfirmasi objek (bbox overlay, selectable list, category correction) → [`SCREEN_SPEC.md §3.22`](SCREEN_SPEC.md)
- S23: Kondisi objek (cleanliness / integrity / reusable context form) → [`SCREEN_SPEC.md §3.23`](SCREEN_SPEC.md)
- S24: Konfirmasi saran (method, reason, guide source, caution, confirm/change) → [`SCREEN_SPEC.md §3.24`](SCREEN_SPEC.md)
- S25: Fallback AI (cause display, retry, manual, close) → [`SCREEN_SPEC.md §3.25`](SCREEN_SPEC.md)

### ★ Komponen AI (C35–C39, BARU)
- C35: Scan action button/FAB (lime, camera icon, "Catat/Scan", ≥48dp) → [`SCREEN_SPEC.md §4`](SCREEN_SPEC.md)
- C36: Camera sheet (ModalBottomSheet + CameraX preview, adaptive height) → [`SCREEN_SPEC.md §4`](SCREEN_SPEC.md)
- C37: Detection review (Canvas bbox overlay + accessible selectable list) → [`SCREEN_SPEC.md §4`](SCREEN_SPEC.md)
- C38: Object context (select/radio untuk 3 dimensi kondisi + unknown default) → [`SCREEN_SPEC.md §4`](SCREEN_SPEC.md)
- C39: Recommendation review (source, reason, caution; confirm; special handling) → [`SCREEN_SPEC.md §4`](SCREEN_SPEC.md)

### ★ API AI (BARU: v1.2)
- GET /v1/ai/objects (catalog object_key/category + mapping version) → [`API.md §10`](API.md)
- POST /v1/ai/detections (upload foto ephemeral, YOLO, simpan session) → [`API.md §10`](API.md)
- POST /v1/ai/recommendations (confirm/correct object, guide, LLM suggestion) → [`API.md §10`](API.md)
- DELETE /v1/ai/detections/{id} (discard session + cleanup foto) → [`API.md §10`](API.md)
- ai_provenance di POST /v1/waste-logs (link session AI ke log) → [`API.md §10`](API.md)
- Error codes baru: AI_TIMEOUT (504), AI_UNAVAILABLE (503), AI_SESSION_EXPIRED (410) → [`API.md §10`](API.md)
- REDUCE tidak boleh dari rekomendasi AI (manual only, no provenance) → [`API.md §10`](API.md)

### ★ Arsitektur AI Android (BARU: §13)
- ScanViewModel + WasteDetectionRepository + package feature/scan, data/ai, core/camera → [`ARCHITECTURE.md §13`](ARCHITECTURE.md)
- Tombol Catat/Scan = action di tengah atas dock lima tab (bukan tab ke-6) → [`ARCHITECTURE.md §13`](ARCHITECTURE.md)
- Go (auth, domain, mapping, LLM validation) vs Python (inference only, no DB/LLM key) → [`ARCHITECTURE.md §13`](ARCHITECTURE.md)
- AI config vars (AI_SCAN_ENABLED, AI_INFERENCE_URL, LLM_PROVIDER, dll.) → [`ARCHITECTURE.md §13`](ARCHITECTURE.md)
- CI tambahan untuk AI (schema/model adapter, mapping, cleanup, camera lifecycle, E2E) → [`ARCHITECTURE.md §13`](ARCHITECTURE.md)

### ★ Implementation Roadmap AI (BARU: §10)
- 8 AI stages (AI-01 Contract & taxonomy → ... → AI-08 Evaluation & pilot) → [`IMPLEMENTATION_PLAN.md §10`](IMPLEMENTATION_PLAN.md)
- AT-AI-01–18 acceptance tests AI → [`IMPLEMENTATION_PLAN.md §10`](IMPLEMENTATION_PLAN.md)
- Demo script AI (2–3 menit, kamera + fallback) → [`IMPLEMENTATION_PLAN.md §10`](IMPLEMENTATION_PLAN.md)
- Gate sebelum pilot AI → [`IMPLEMENTATION_PLAN.md §10`](IMPLEMENTATION_PLAN.md)

---

## Cari informasi berdasarkan topik (Core: tidak berubah)

### Warna & tema
- Token semantik (primary, surface, error, dll.) → [`DESIGN.md §2`](DESIGN.md)
- Gradasi pine–lime, glassmorphism dock → [`DESIGN.md §2 Material v1.1`](DESIGN.md)
- Handoff Compose (`WasteNotTheme`, ColorScheme, dynamic color off) → [`DESIGN.md §10`](DESIGN.md)

### Tipografi
- Semua role typography (displayLarge hingga labelSmall) → [`DESIGN.md §3`](DESIGN.md)
- Setup font Plus Jakarta Sans di Compose → [`DESIGN.md §3 Font di Compose`](DESIGN.md)

### Spacing, ukuran, bentuk, elevasi
- Token spacing space1–space12 → [`DESIGN.md §4`](DESIGN.md)
- Touch target minimal 48 dp, radius, elevasi → [`DESIGN.md §4`](DESIGN.md)

### Layout & breakpoint
- <600 dp / 600–839 dp / ≥840 dp → [`DESIGN.md §5`](DESIGN.md)
- Safe area, insets, keyboard → [`ARCHITECTURE.md §7`](ARCHITECTURE.md)

### Komponen UI (C01–C34): core
- Inventaris C01–C39 (termasuk AI) → [`SCREEN_SPEC.md §4`](SCREEN_SPEC.md)
- Aturan visual per kelompok → [`DESIGN.md §6`](DESIGN.md)

### Layar core (S01–S20)
- Inventaris + prioritas + route → [`SCREEN_SPEC.md §2`](SCREEN_SPEC.md)
- Auth: S01 (session), S02 (login), S03 (register) → [`SCREEN_SPEC.md §3`](SCREEN_SPEC.md)
- Utama: S04 (home), S07 (history), S11 (guide), S13 (statistics), S14 (profile) → [`SCREEN_SPEC.md §3`](SCREEN_SPEC.md)
- Editor log: S05 (create), S09 (edit) → [`SCREEN_SPEC.md §3`](SCREEN_SPEC.md)

### Domain & metrik
- Reduce, Reuse, Recycle, Trash definitions → [`PRD.md §6`](PRD.md)
- Recycling rate (null vs 0, basis) → [`PRD.md §9`](PRD.md)
- XP per log, deduction delete, level → [`PRD.md §8`](PRD.md)
- Acceptance tests AT-01–AT-26 (core) → [`IMPLEMENTATION_PLAN.md §3`](IMPLEMENTATION_PLAN.md)

### API & network (core)
- Enum (category, method, quantity_kind) → [`API.md §2.2`](API.md)
- Entities (users, sessions, waste_logs, dll.) → [`API.md §2.1`](API.md)
- Auth endpoints (register, login, refresh, logout) → [`API.md §3`](API.md)
- Waste log CRUD → [`API.md §4`](API.md)
- Idempoten `client_request_id` → [`API.md §4`](API.md)

### Navigasi & arsitektur Android (core)
- Routes + argumen → [`ARCHITECTURE.md §6`](ARCHITECTURE.md)
- UDF flow, state daftar/editor → [`ARCHITECTURE.md §4`](ARCHITECTURE.md)
- Token storage (Keystore) → [`ARCHITECTURE.md §8`](ARCHITECTURE.md)

### Aksesibilitas & microcopy
- Touch target, focus, font scale, reduced motion → [`DESIGN.md §9`](DESIGN.md)
- Contoh teks state (empty/loading/error/success) → [`DESIGN.md §8`](DESIGN.md)

---

## Apa yang TIDAK ada di design kit ini

- Backend implementation (Go, PostgreSQL, Python YOLO worker), hanya kontrak di `API.md` dan `ARCHITECTURE.md`
- Trained/fine-tuned YOLO model, belum dipilih, lihat `AI_SPEC.md §9`
- LLM provider yang sudah dipilih, belum diputuskan tim
- APK atau Android project yang sudah di-build
- Credential, token, atau data pengguna nyata
- Dataset fine-tuning (lisensi belum diputuskan)

---

*Versi kit: README v1.3, PRD/ARCHITECTURE/API/IMPLEMENTATION_PLAN v1.2, DESIGN/SCREEN_SPEC v1.3 · 28 Sep 2026. Verifikasi versi dependency di [`gradle/libs.versions.toml`](../../../gradle/libs.versions.toml).*
