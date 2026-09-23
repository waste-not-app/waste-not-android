# =============================================================================
# WasteNot Android — Makefile
# =============================================================================
# Penggunaan: make <target>
# Contoh    : make build, make run, make branch TICKET=W1-03 NAME=theme-setup
#
# Prasyarat : GNU Make, JDK 17+, Android SDK, adb, git
# Dokumen   : CLAUDE.md, AGENT.md, docs/WasteNot-Product-Design-Kit/
# =============================================================================

# ---------------------------------------------------------------------------
# Konfigurasi default
# ---------------------------------------------------------------------------

GRADLEW      := ./gradlew
APP_ID       := com.example.wastenot
MAIN_BRANCH  := main
DEV_BRANCH   := develop

# Variabel yang bisa dioverride dari CLI
TICKET  ?= WN-000
NAME    ?= feature
WEEK    ?= 1
DEVICE  ?= $(shell adb devices -l 2>/dev/null | awk 'NR==2{print $$1}')

# Warna terminal
RED     := \033[0;31m
GREEN   := \033[0;32m
YELLOW  := \033[1;33m
BLUE    := \033[0;34m
CYAN    := \033[0;36m
BOLD    := \033[1m
RESET   := \033[0m

# Cek gradlew tersedia
$(if $(wildcard $(GRADLEW)),,$(error "gradlew tidak ditemukan. Pastikan kamu di root project WasteNot."))

.DEFAULT_GOAL := help
.PHONY: help build build-release run install uninstall clean \
        test test-unit test-device lint check \
        nav-graph nav-html nav-mermaid nav-lint nav-dump nav-check \
        branch branch-feature branch-fix branch-release branch-hotfix \
        merge-dev merge-main pr-ready \
        week1 week2 week3 week4 \
        setup setup-hooks device-info screenshots


# =============================================================================
# HELP
# =============================================================================

help: ## Tampilkan semua perintah yang tersedia
	@echo ""
	@echo "  $(BOLD)$(CYAN)WasteNot Android — Makefile$(RESET)"
	@echo ""
	@echo "  $(BOLD)BUILD$(RESET)"
	@grep -E '^(build|run|install|uninstall|clean)[^:]*:.*##' $(MAKEFILE_LIST) \
		| awk 'BEGIN{FS=":.*##"}{printf "  $(GREEN)%-28s$(RESET) %s\n", $$1, $$2}'
	@echo ""
	@echo "  $(BOLD)TEST & QUALITY$(RESET)"
	@grep -E '^(test|lint|check)[^:]*:.*##' $(MAKEFILE_LIST) \
		| awk 'BEGIN{FS=":.*##"}{printf "  $(GREEN)%-28s$(RESET) %s\n", $$1, $$2}'
	@echo ""
	@echo "  $(BOLD)NAVIGATION GRAPH$(RESET)"
	@grep -E '^nav-[^:]+:.*##' $(MAKEFILE_LIST) \
		| awk 'BEGIN{FS=":.*##"}{printf "  $(GREEN)%-28s$(RESET) %s\n", $$1, $$2}'
	@echo ""
	@echo "  $(BOLD)BRANCHING$(RESET)"
	@grep -E '^(branch|merge|pr)[^:]*:.*##' $(MAKEFILE_LIST) \
		| awk 'BEGIN{FS=":.*##"}{printf "  $(GREEN)%-28s$(RESET) %s\n", $$1, $$2}'
	@echo ""
	@echo "  $(BOLD)WEEKLY GATES$(RESET)"
	@grep -E '^week[0-9]+:.*##' $(MAKEFILE_LIST) \
		| awk 'BEGIN{FS=":.*##"}{printf "  $(GREEN)%-28s$(RESET) %s\n", $$1, $$2}'
	@echo ""
	@echo "  $(BOLD)SETUP & DEVICE$(RESET)"
	@grep -E '^(setup|device|screenshots)[^:]*:.*##' $(MAKEFILE_LIST) \
		| awk 'BEGIN{FS=":.*##"}{printf "  $(GREEN)%-28s$(RESET) %s\n", $$1, $$2}'
	@echo ""
	@echo "  $(BOLD)CONTOH PENGGUNAAN$(RESET)"
	@echo "  $(YELLOW)make branch-feature TICKET=W1-03 NAME=theme-setup$(RESET)"
	@echo "  $(YELLOW)make branch-fix     TICKET=W2-04 NAME=conflict-409$(RESET)"
	@echo "  $(YELLOW)make build$(RESET)         → build debug APK"
	@echo "  $(YELLOW)make run$(RESET)           → build + install + launch di device"
	@echo "  $(YELLOW)make pr-ready$(RESET)      → lint + test + nav-check sebelum push PR"
	@echo "  $(YELLOW)make week1$(RESET)         → cek gate Minggu 1 (build + auth test)"
	@echo ""


# =============================================================================
# BUILD
# =============================================================================

build: ## Build debug APK
	@echo "$(BLUE)▶ Build debug APK…$(RESET)"
	$(GRADLEW) assembleDebug
	@echo "$(GREEN)✔ APK: app/build/outputs/apk/debug/app-debug.apk$(RESET)"

build-release: ## Build release APK (perlu signing config)
	@echo "$(BLUE)▶ Build release APK…$(RESET)"
	$(GRADLEW) assembleRelease
	@echo "$(GREEN)✔ Release APK siap$(RESET)"

run: build ## Build + install + launch di device/emulator
	@echo "$(BLUE)▶ Install ke device: $(DEVICE)$(RESET)"
	@if [ -z "$(DEVICE)" ]; then \
		echo "$(RED)✗ Tidak ada device/emulator yang terdeteksi. Jalankan emulator dulu.$(RESET)"; \
		exit 1; \
	fi
	adb -s $(DEVICE) install -r app/build/outputs/apk/debug/app-debug.apk
	adb -s $(DEVICE) shell am start -n $(APP_ID)/.MainActivity
	@echo "$(GREEN)✔ App berjalan di $(DEVICE)$(RESET)"

install: ## Install APK yang sudah ada ke device (tanpa build ulang)
	@if [ -z "$(DEVICE)" ]; then echo "$(RED)✗ Tidak ada device terdeteksi$(RESET)"; exit 1; fi
	adb -s $(DEVICE) install -r app/build/outputs/apk/debug/app-debug.apk
	@echo "$(GREEN)✔ APK ter-install$(RESET)"

uninstall: ## Hapus app dari device (termasuk data)
	@if [ -z "$(DEVICE)" ]; then echo "$(RED)✗ Tidak ada device terdeteksi$(RESET)"; exit 1; fi
	@read -p "$(YELLOW)Hapus $(APP_ID) dan semua datanya? [y/N]$(RESET) " ans; \
	[ "$$ans" = "y" ] && adb -s $(DEVICE) uninstall $(APP_ID) && echo "$(GREEN)✔ App dihapus$(RESET)" || echo "Dibatalkan."

clean: ## Hapus semua build artifact
	@echo "$(BLUE)▶ Clean build…$(RESET)"
	$(GRADLEW) clean
	@echo "$(GREEN)✔ Build directory bersih$(RESET)"


# =============================================================================
# TEST & QUALITY
# =============================================================================

test: test-unit ## Alias: jalankan semua unit test

test-unit: ## Jalankan unit test (tanpa device)
	@echo "$(BLUE)▶ Unit tests…$(RESET)"
	$(GRADLEW) test
	@echo "$(GREEN)✔ Unit tests selesai. Report: app/build/reports/tests/$(RESET)"

test-device: ## Jalankan instrumented test di device
	@if [ -z "$(DEVICE)" ]; then echo "$(RED)✗ Tidak ada device terdeteksi$(RESET)"; exit 1; fi
	@echo "$(BLUE)▶ Instrumented tests di $(DEVICE)…$(RESET)"
	$(GRADLEW) connectedDebugAndroidTest
	@echo "$(GREEN)✔ Instrumented tests selesai$(RESET)"

lint: ## Jalankan Android Lint
	@echo "$(BLUE)▶ Android Lint…$(RESET)"
	$(GRADLEW) lintDebug
	@echo "$(GREEN)✔ Lint report: app/build/reports/lint-results-debug.html$(RESET)"

check: lint test-unit ## Lint + unit test (jalankan sebelum commit)
	@echo "$(GREEN)✔ check selesai — aman untuk commit$(RESET)"


# =============================================================================
# NAVIGATION GRAPH
# =============================================================================

nav-graph: ## Generate data nav graph (nav-graph.json)
	@echo "$(BLUE)▶ Generate nav graph…$(RESET)"
	$(GRADLEW) :app:generateNavGraph
	@echo "$(GREEN)✔ Data: app/build/navgraph/nav-graph.json$(RESET)"

nav-html: ## Export nav graph sebagai HTML interaktif dan buka di browser
	@echo "$(BLUE)▶ Export nav graph HTML…$(RESET)"
	$(GRADLEW) :app:exportNavGraphHtml
	@echo "$(GREEN)✔ File: app/build/navgraph/nav-graph.html$(RESET)"
	@xdg-open app/build/navgraph/nav-graph.html 2>/dev/null || \
		echo "$(YELLOW)Buka manual: app/build/navgraph/nav-graph.html$(RESET)"

nav-mermaid: ## Export nav graph sebagai diagram Mermaid (untuk Markdown/PR)
	@echo "$(BLUE)▶ Export Mermaid diagram…$(RESET)"
	$(GRADLEW) :app:exportNavGraphMermaid
	@echo "$(GREEN)✔ File: app/build/navgraph/nav-graph.mmd$(RESET)"
	@cat app/build/navgraph/nav-graph.mmd

nav-lint: ## Cek masalah navigasi (unreachable screen, duplicate start, dll.)
	@echo "$(BLUE)▶ Nav lint…$(RESET)"
	$(GRADLEW) :app:navLint

nav-dump: ## Commit state nav graph sebagai baseline .nav
	@echo "$(BLUE)▶ Dump nav baseline…$(RESET)"
	$(GRADLEW) :app:navDump
	@echo "$(GREEN)✔ Baseline tersimpan. Commit file .nav ke git.$(RESET)"

nav-check: ## Validasi nav graph terhadap baseline .nav (pakai di CI)
	@echo "$(BLUE)▶ Nav check vs baseline…$(RESET)"
	$(GRADLEW) :app:navCheck


# =============================================================================
# BRANCHING STRATEGY
#
# Konvensi nama branch:
#   feature/W{week}-{ticket}_{name}   → fitur baru per minggu
#   fix/W{week}-{ticket}_{name}       → bug fix dalam sprint
#   hotfix/{ticket}_{name}            → fix kritis langsung ke main
#   release/week-{week}               → branch rilis akhir sprint
#   chore/{name}                      → task non-fungsional (deps, config)
#   docs/{name}                       → perubahan dokumentasi saja
#
# Contoh:
#   make branch-feature TICKET=W1-03 NAME=theme-setup
#   make branch-fix     TICKET=W2-04 NAME=conflict-409
#   make branch-release WEEK=1
# =============================================================================

branch: ## Buat branch custom: make branch TICKET=WN-001 NAME=my-task TYPE=feature
	@$(MAKE) _branch-create PREFIX="$(TYPE)/$(TICKET)_$(NAME)"

branch-feature: ## Buat feature branch: make branch-feature TICKET=W1-03 NAME=theme-setup
	@$(MAKE) _branch-create PREFIX="feature/$(TICKET)_$(NAME)"

branch-fix: ## Buat fix branch: make branch-fix TICKET=W2-04 NAME=conflict-409
	@$(MAKE) _branch-create PREFIX="fix/$(TICKET)_$(NAME)"

branch-hotfix: ## Buat hotfix branch dari main: make branch-hotfix TICKET=WN-010 NAME=crash-auth
	@echo "$(YELLOW)▶ Hotfix dari $(MAIN_BRANCH)…$(RESET)"
	git fetch origin
	git checkout $(MAIN_BRANCH)
	git pull origin $(MAIN_BRANCH)
	git checkout -b "hotfix/$(TICKET)_$(NAME)"
	@echo "$(GREEN)✔ Branch: hotfix/$(TICKET)_$(NAME) (dari $(MAIN_BRANCH))$(RESET)"
	@echo "$(YELLOW)Setelah selesai: make merge-main lalu make merge-dev$(RESET)"

branch-release: ## Buat release branch: make branch-release WEEK=1
	@echo "$(BLUE)▶ Release branch dari $(DEV_BRANCH)…$(RESET)"
	git fetch origin
	git checkout $(DEV_BRANCH)
	git pull origin $(DEV_BRANCH)
	git checkout -b "release/week-$(WEEK)"
	@echo "$(GREEN)✔ Branch: release/week-$(WEEK)$(RESET)"
	@echo "$(YELLOW)Jalankan: make week$(WEEK) untuk cek gate minggu ini$(RESET)"

branch-chore: ## Buat chore branch: make branch-chore NAME=update-deps
	@$(MAKE) _branch-create PREFIX="chore/$(NAME)"

branch-docs: ## Buat docs branch: make branch-docs NAME=update-screen-spec
	@$(MAKE) _branch-create PREFIX="docs/$(NAME)"

# Internal helper
_branch-create:
	@if [ -z "$(NAME)" ] || [ "$(NAME)" = "feature" ]; then \
		echo "$(RED)✗ Wajib isi NAME dan TICKET. Contoh: make branch-feature TICKET=W1-03 NAME=theme-setup$(RESET)"; \
		exit 1; \
	fi
	git fetch origin
	git checkout $(DEV_BRANCH) 2>/dev/null || git checkout -b $(DEV_BRANCH) origin/$(DEV_BRANCH)
	git pull origin $(DEV_BRANCH)
	git checkout -b "$(PREFIX)"
	@echo "$(GREEN)✔ Branch: $(PREFIX) (dari $(DEV_BRANCH))$(RESET)"
	@echo "$(YELLOW)Push: git push -u origin $(PREFIX)$(RESET)"

merge-dev: ## Merge branch aktif ke develop (interaktif)
	@BRANCH=$$(git rev-parse --abbrev-ref HEAD); \
	echo "$(YELLOW)▶ Merge '$$BRANCH' → $(DEV_BRANCH)$(RESET)"; \
	read -p "Lanjut? [y/N] " ans; \
	[ "$$ans" = "y" ] || exit 0; \
	$(MAKE) check || (echo "$(RED)✗ check gagal. Perbaiki dulu.$(RESET)" && exit 1); \
	git checkout $(DEV_BRANCH) && git pull origin $(DEV_BRANCH) && \
	git merge --no-ff "$$BRANCH" -m "Merge $$BRANCH → $(DEV_BRANCH)" && \
	git push origin $(DEV_BRANCH) && \
	echo "$(GREEN)✔ Merged ke $(DEV_BRANCH)$(RESET)"

merge-main: ## Merge develop ke main (untuk release/hotfix)
	@echo "$(RED)⚠ Merge ke $(MAIN_BRANCH) — pastikan QA sudah lulus!$(RESET)"
	@read -p "Lanjut? [y/N] " ans; [ "$$ans" = "y" ] || exit 0
	git checkout $(MAIN_BRANCH) && git pull origin $(MAIN_BRANCH)
	git merge --no-ff $(DEV_BRANCH) -m "Release: merge $(DEV_BRANCH) → $(MAIN_BRANCH)"
	git push origin $(MAIN_BRANCH)
	@echo "$(GREEN)✔ $(MAIN_BRANCH) diperbarui. Tag versinya: git tag v0.x.0$(RESET)"

pr-ready: ## Cek semua sebelum buat PR (lint + test + nav-check)
	@echo "$(BLUE)▶ PR readiness check…$(RESET)"
	@$(MAKE) check
	@$(MAKE) nav-check
	@echo ""
	@echo "$(GREEN)$(BOLD)✔ Siap PR!$(RESET) Branch: $$(git rev-parse --abbrev-ref HEAD)"
	@echo "$(CYAN)Ringkasan commit:$(RESET)"
	@git log --oneline origin/$(DEV_BRANCH)..HEAD 2>/dev/null || git log --oneline -5


# =============================================================================
# WEEKLY GATES
# Berdasarkan IMPLEMENTATION_PLAN.md §2
# =============================================================================

week1: ## Gate Minggu 1: fondasi + auth (build debug + unit test)
	@echo "$(BOLD)$(CYAN)=== Gate Minggu 1: Fondasi dan Autentikasi ===$(RESET)"
	@echo "Checklist:"
	@echo "  [ ] W1-01: Definisi metrik disetujui (cek manual)"
	@echo "  [ ] W1-02: Build debug berjalan → jalankan: make build"
	@echo "  [ ] W1-03: Theme dan preview terang/gelap"
	@echo "  [ ] W1-04: Navigasi 5 tab + back berfungsi"
	@echo "  [ ] W1-05: Backend health endpoint merespons"
	@echo "  [ ] W1-06: Register/login/logout nyata"
	@echo "  [ ] W1-07: S01–S03 + validasi error"
	@echo ""
	@$(MAKE) build
	@$(MAKE) test-unit
	@echo ""
	@echo "$(YELLOW)Gate lulus jika: build sukses + test lulus + satu akun bisa login/logout$(RESET)"

week2: ## Gate Minggu 2: tracking lengkap (build + test + nav-check)
	@echo "$(BOLD)$(CYAN)=== Gate Minggu 2: Tracking Lengkap ===$(RESET)"
	@echo "Checklist:"
	@echo "  [ ] W2-01: Create log + idempoten client_request_id"
	@echo "  [ ] W2-02: Editor + draft restore"
	@echo "  [ ] W2-03: List/detail/filter/pagination S07–S10"
	@echo "  [ ] W2-04: Update/delete + version conflict 409"
	@echo "  [ ] W2-05: Dashboard aggregate update otomatis"
	@echo "  [ ] W2-06: Isolasi data antar akun"
	@echo ""
	@$(MAKE) check
	@$(MAKE) nav-check
	@echo "$(YELLOW)Gate lulus jika: CRUD lengkap + data A/B tidak bocor + dashboard dinamis$(RESET)"

week3: ## Gate Minggu 3: panduan + statistik (full check + nav-graph)
	@echo "$(BOLD)$(CYAN)=== Gate Minggu 3: Panduan dan Evaluasi ===$(RESET)"
	@echo "Checklist:"
	@echo "  [ ] W3-01: 12–20 artikel panduan ditinjau"
	@echo "  [ ] W3-02: Search/list/detail panduan + no-result"
	@echo "  [ ] W3-03: Statistik + seri harian + kategori/metode"
	@echo "  [ ] W3-04: Chart + alternatif teks + 7/30 hari"
	@echo "  [ ] W3-05: XP dan level baseline"
	@echo "  [ ] W3-06: Streak/achievement (jika core stabil)"
	@echo ""
	@$(MAKE) check
	@$(MAKE) nav-html
	@echo "$(YELLOW)Gate lulus jika: demo login→statistik berjalan di server nyata$(RESET)"

week4: ## Gate Minggu 4: ketahanan + demo (full check + build-release)
	@echo "$(BOLD)$(CYAN)=== Gate Minggu 4: Ketahanan dan Demo ===$(RESET)"
	@echo "Checklist:"
	@echo "  [ ] W4-01: Regression test terisi bukti"
	@echo "  [ ] W4-02: TalkBack + 200% font + landscape"
	@echo "  [ ] W4-03: Build demo + staging config"
	@echo "  [ ] W4-04: Reminder (jika core lulus)"
	@echo "  [ ] W4-07: Demo direkam, bug list final dicatat"
	@echo ""
	@$(MAKE) check
	@$(MAKE) nav-check
	@$(MAKE) build-release 2>/dev/null || echo "$(YELLOW)Release build perlu signing config$(RESET)"
	@echo "$(YELLOW)Gate lulus jika: demo end-to-end stabil di perangkat demo$(RESET)"


# =============================================================================
# SETUP & TOOLS
# =============================================================================

setup: ## Setup pertama kali: cek tools, buat branch develop, build awal
	@echo "$(BOLD)$(CYAN)=== Setup WasteNot Android ===$(RESET)"
	@echo ""
	@echo "$(BLUE)▶ Cek prasyarat…$(RESET)"
	@command -v git  >/dev/null 2>&1 && echo "  $(GREEN)✔ git$(RESET)"   || echo "  $(RED)✗ git tidak ditemukan$(RESET)"
	@command -v java >/dev/null 2>&1 && echo "  $(GREEN)✔ java$(RESET)"  || echo "  $(RED)✗ java tidak ditemukan$(RESET)"
	@command -v adb  >/dev/null 2>&1 && echo "  $(GREEN)✔ adb$(RESET)"   || echo "  $(RED)✗ adb tidak ditemukan$(RESET)"
	@test -x $(GRADLEW)            && echo "  $(GREEN)✔ gradlew$(RESET)" || echo "  $(RED)✗ gradlew tidak ada$(RESET)"
	@echo ""
	@echo "$(BLUE)▶ Setup git branch structure…$(RESET)"
	@git branch -a 2>/dev/null | grep -q "$(DEV_BRANCH)" && \
		echo "  Branch $(DEV_BRANCH) sudah ada" || \
		(git checkout -b $(DEV_BRANCH) && echo "  $(GREEN)✔ Branch $(DEV_BRANCH) dibuat$(RESET)")
	@echo ""
	@echo "$(BLUE)▶ Install git hooks…$(RESET)"
	@$(MAKE) setup-hooks
	@echo ""
	@echo "$(BLUE)▶ Build awal…$(RESET)"
	@$(MAKE) build
	@echo ""
	@echo "$(GREEN)$(BOLD)✔ Setup selesai!$(RESET)"
	@echo ""
	@echo "  Langkah berikutnya:"
	@echo "  1. Install Android Studio plugins:"
	@echo "     - Compose HotSwan (hot reload)"
	@echo "     - Compose Navigation Graph (visualisasi nav)"
	@echo "  2. Baca $(BOLD)CLAUDE.md$(RESET) dan $(BOLD)docs/WasteNot-Product-Design-Kit/$(RESET)"
	@echo "  3. Mulai: $(YELLOW)make branch-feature TICKET=W1-03 NAME=theme-setup$(RESET)"

setup-hooks: ## Install git pre-commit hook (cek lint + test sebelum commit)
	@mkdir -p .git/hooks
	@printf '#!/bin/sh\n# WasteNot pre-commit hook\necho "▶ Pre-commit: lint + unit test..."\n./gradlew lintDebug test --daemon -q\nif [ $$? -ne 0 ]; then\n  echo "✗ Lint atau test gagal. Commit dibatalkan."\n  echo "  Jalankan '"'"'make check'"'"' untuk detail."\n  exit 1\nfi\necho "✔ Pre-commit check lulus."\n' > .git/hooks/pre-commit
	@chmod +x .git/hooks/pre-commit
	@echo "$(GREEN)✔ Git pre-commit hook terpasang$(RESET)"

device-info: ## Tampilkan info device/emulator yang terkoneksi
	@echo "$(BOLD)Device yang terkoneksi:$(RESET)"
	@adb devices -l
	@echo ""
	@if [ -n "$(DEVICE)" ]; then \
		echo "$(BOLD)Device aktif: $(DEVICE)$(RESET)"; \
		adb -s $(DEVICE) shell getprop ro.product.model 2>/dev/null | xargs -I{} echo "  Model  : {}"; \
		adb -s $(DEVICE) shell getprop ro.build.version.release 2>/dev/null | xargs -I{} echo "  Android: {}"; \
		adb -s $(DEVICE) shell wm size 2>/dev/null | xargs -I{} echo "  Layar  : {}"; \
	else \
		echo "$(YELLOW)Tidak ada device aktif. Pastikan emulator berjalan atau USB debugging aktif.$(RESET)"; \
	fi

screenshots: ## Ambil screenshot dari semua device yang terkoneksi
	@echo "$(BLUE)▶ Ambil screenshot…$(RESET)"
	@mkdir -p screenshots
	@TIMESTAMP=$$(date +%Y%m%d_%H%M%S); \
	for DEV in $$(adb devices | awk 'NR>1 && $$2=="device"{print $$1}'); do \
		FILE="screenshots/$$DEV_$$TIMESTAMP.png"; \
		adb -s $$DEV exec-out screencap -p > "$$FILE" && \
		echo "  $(GREEN)✔ $$FILE$(RESET)"; \
	done
