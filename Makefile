# =============================================================================
# WasteNot Android: Makefile
# =============================================================================
# Usage     : make <target>
# Examples  : make build, make run, make branch TICKET=W1-03 NAME=theme-setup
#
# Requires  : GNU Make, JDK 17+, Android SDK, adb, git
# Docs      : AGENTS.md, docs/design-kit/
# =============================================================================

# ---------------------------------------------------------------------------
# Defaults
# ---------------------------------------------------------------------------

GRADLEW      := ./gradlew
APP_ID       := com.example.wastenot
MAIN_BRANCH  := main
DEV_BRANCH   := develop

# Overridable from the command line
TICKET  ?= WN-000
NAME    ?= feature
WEEK    ?= 1
DEVICE  ?= $(shell adb devices -l 2>/dev/null | awk 'NR==2{print $$1}')

# Terminal colors
RED     := \033[0;31m
GREEN   := \033[0;32m
YELLOW  := \033[1;33m
BLUE    := \033[0;34m
CYAN    := \033[0;36m
BOLD    := \033[1m
RESET   := \033[0m

# Require the Gradle wrapper
$(if $(wildcard $(GRADLEW)),,$(error "gradlew not found. Run make from the WasteNot project root."))

.DEFAULT_GOAL := help
.PHONY: help build build-release run install uninstall clean \
        test test-unit test-device lint check \
        nav-graph nav-html nav-mermaid nav-lint nav-dump nav-check \
        branch branch-feature branch-fix branch-release branch-hotfix \
        merge-dev merge-main pr-ready \
        week1 week2 week3 week4 \
        setup setup-hooks device-info screenshots check-ci docker-apk docker-ci


# =============================================================================
# HELP
# =============================================================================

help: ## Show all available targets
	@echo ""
	@echo "  $(BOLD)$(CYAN)WasteNot Android: Makefile$(RESET)"
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
	@echo "  $(BOLD)DOCKER$(RESET)"
	@grep -E '^docker-[^:]+:.*##' $(MAKEFILE_LIST) \
		| awk 'BEGIN{FS=":.*##"}{printf "  $(GREEN)%-28s$(RESET) %s\n", $$1, $$2}'
	@echo ""
	@echo "  $(BOLD)SETUP & DEVICE$(RESET)"
	@grep -E '^(setup|device|screenshots)[^:]*:.*##' $(MAKEFILE_LIST) \
		| awk 'BEGIN{FS=":.*##"}{printf "  $(GREEN)%-28s$(RESET) %s\n", $$1, $$2}'
	@echo ""
	@echo "  $(BOLD)EXAMPLES$(RESET)"
	@echo "  $(YELLOW)make branch-feature TICKET=W1-03 NAME=theme-setup$(RESET)"
	@echo "  $(YELLOW)make branch-fix     TICKET=W2-04 NAME=conflict-409$(RESET)"
	@echo "  $(YELLOW)make build$(RESET)         → build debug APK"
	@echo "  $(YELLOW)make run$(RESET)           → build, install, and launch on a device"
	@echo "  $(YELLOW)make pr-ready$(RESET)      → lint, test, and nav-check before opening a PR"
	@echo "  $(YELLOW)make week1$(RESET)         → run the week 1 gate (build and auth tests)"
	@echo ""


# =============================================================================
# BUILD
# =============================================================================

build: ## Build the debug APK
	@echo "$(BLUE)▶ Build debug APK…$(RESET)"
	$(GRADLEW) assembleDebug
	@echo "$(GREEN)✔ APK: app/build/outputs/apk/debug/app-debug.apk$(RESET)"

build-release: ## Build the release APK (requires signing config)
	@echo "$(BLUE)▶ Build release APK…$(RESET)"
	$(GRADLEW) assembleRelease
	@echo "$(GREEN)✔ Release APK ready$(RESET)"

run: build ## Build, install, and launch on a device
	@echo "$(BLUE)▶ Installing on device: $(DEVICE)$(RESET)"
	@if [ -z "$(DEVICE)" ]; then \
		echo "$(RED)✗ No device detected. Connect a device with USB debugging enabled.$(RESET)"; \
		exit 1; \
	fi
	adb -s $(DEVICE) install -r app/build/outputs/apk/debug/app-debug.apk
	adb -s $(DEVICE) shell am start -n $(APP_ID)/.MainActivity
	@echo "$(GREEN)✔ App running on $(DEVICE)$(RESET)"

install: ## Install the existing debug APK without rebuilding
	@if [ -z "$(DEVICE)" ]; then echo "$(RED)✗ No device detected$(RESET)"; exit 1; fi
	adb -s $(DEVICE) install -r app/build/outputs/apk/debug/app-debug.apk
	@echo "$(GREEN)✔ APK installed$(RESET)"

uninstall: ## Remove the app and its data from the device
	@if [ -z "$(DEVICE)" ]; then echo "$(RED)✗ No device detected$(RESET)"; exit 1; fi
	@read -p "$(YELLOW)Remove $(APP_ID) and all of its data? [y/N]$(RESET) " ans; \
	[ "$$ans" = "y" ] && adb -s $(DEVICE) uninstall $(APP_ID) && echo "$(GREEN)✔ App removed$(RESET)" || echo "Cancelled."

clean: ## Remove all build artifacts
	@echo "$(BLUE)▶ Clean build…$(RESET)"
	$(GRADLEW) clean
	@echo "$(GREEN)✔ Build directory cleaned$(RESET)"


# =============================================================================
# TEST & QUALITY
# =============================================================================

test: test-unit ## Alias for test-unit

test-unit: ## Run unit tests (no device needed)
	@echo "$(BLUE)▶ Unit tests…$(RESET)"
	$(GRADLEW) test
	@echo "$(GREEN)✔ Unit tests finished. Report: app/build/reports/tests/$(RESET)"

test-device: ## Run instrumented tests on a device
	@if [ -z "$(DEVICE)" ]; then echo "$(RED)✗ No device detected$(RESET)"; exit 1; fi
	@echo "$(BLUE)▶ Instrumented tests on $(DEVICE)…$(RESET)"
	$(GRADLEW) connectedDebugAndroidTest
	@echo "$(GREEN)✔ Instrumented tests finished$(RESET)"

lint: ## Run Android Lint
	@echo "$(BLUE)▶ Android Lint…$(RESET)"
	$(GRADLEW) lintDebug
	@echo "$(GREEN)✔ Lint report: app/build/reports/lint-results-debug.html$(RESET)"

check: lint test-unit ## Lint and unit tests (run before committing)
	@echo "$(GREEN)✔ check passed, safe to commit$(RESET)"

check-ci: ## Full CI gate: lint all variants, unit tests, build all APKs
	$(GRADLEW) lintDebug lintStaging lintRelease testDebugUnitTest -Pwastenot.hotReload=false
	$(GRADLEW) assembleDebug assembleStaging assembleRelease assembleDebugAndroidTest -Pwastenot.hotReload=false


# =============================================================================
# NAVIGATION GRAPH
# =============================================================================

nav-graph: ## Generate navigation graph data (nav-graph.json)
	@echo "$(BLUE)▶ Generate nav graph…$(RESET)"
	$(GRADLEW) :app:generateNavGraph
	@echo "$(GREEN)✔ Data: app/build/navgraph/nav-graph.json$(RESET)"

nav-html: ## Export the navigation graph as interactive HTML and open it
	@echo "$(BLUE)▶ Export nav graph HTML…$(RESET)"
	$(GRADLEW) :app:exportNavGraphHtml
	@echo "$(GREEN)✔ File: app/build/navgraph/nav-graph.html$(RESET)"
	@xdg-open app/build/navgraph/nav-graph.html 2>/dev/null || \
		echo "$(YELLOW)Open manually: app/build/navgraph/nav-graph.html$(RESET)"

nav-mermaid: ## Export the navigation graph as a Mermaid diagram
	@echo "$(BLUE)▶ Export Mermaid diagram…$(RESET)"
	$(GRADLEW) :app:exportNavGraphMermaid
	@echo "$(GREEN)✔ File: app/build/navgraph/nav-graph.mmd$(RESET)"
	@cat app/build/navgraph/nav-graph.mmd

nav-lint: ## Find navigation issues (unreachable screens, duplicate starts)
	@echo "$(BLUE)▶ Nav lint…$(RESET)"
	$(GRADLEW) :app:navLint

nav-dump: ## Save the navigation graph as the .nav baseline
	@echo "$(BLUE)▶ Dump nav baseline…$(RESET)"
	$(GRADLEW) :app:navDump
	@echo "$(GREEN)✔ Baseline saved. Commit the .nav file.$(RESET)"

nav-check: ## Check the navigation graph against the .nav baseline
	@echo "$(BLUE)▶ Nav check vs baseline…$(RESET)"
	$(GRADLEW) :app:navCheck


# =============================================================================
# BRANCHING STRATEGY
#
# Branch naming:
#   feature/W{week}-{ticket}_{name}   → new feature
#   fix/W{week}-{ticket}_{name}       → bug fix
#   hotfix/{ticket}_{name}            → critical fix from main
#   release/week-{week}               → end-of-sprint release
#   chore/{name}                      → non-functional work (deps, config)
#   docs/{name}                       → documentation only
#
# Examples:
#   make branch-feature TICKET=W1-03 NAME=theme-setup
#   make branch-fix     TICKET=W2-04 NAME=conflict-409
#   make branch-release WEEK=1
# =============================================================================

branch: ## Create a custom branch: make branch TICKET=WN-001 NAME=my-task TYPE=feature
	@$(MAKE) _branch-create PREFIX="$(TYPE)/$(TICKET)_$(NAME)"

branch-feature: ## Create a feature branch: make branch-feature TICKET=W1-03 NAME=theme-setup
	@$(MAKE) _branch-create PREFIX="feature/$(TICKET)_$(NAME)"

branch-fix: ## Create a fix branch: make branch-fix TICKET=W2-04 NAME=conflict-409
	@$(MAKE) _branch-create PREFIX="fix/$(TICKET)_$(NAME)"

branch-hotfix: ## Create a hotfix branch from main: make branch-hotfix TICKET=WN-010 NAME=crash-auth
	@echo "$(YELLOW)▶ Hotfix from $(MAIN_BRANCH)…$(RESET)"
	git fetch origin
	git checkout $(MAIN_BRANCH)
	git pull origin $(MAIN_BRANCH)
	git checkout -b "hotfix/$(TICKET)_$(NAME)"
	@echo "$(GREEN)✔ Branch: hotfix/$(TICKET)_$(NAME) (from $(MAIN_BRANCH))$(RESET)"
	@echo "$(YELLOW)When done: make merge-main, then make merge-dev$(RESET)"

branch-release: ## Create a release branch: make branch-release WEEK=1
	@echo "$(BLUE)▶ Release branch from $(DEV_BRANCH)…$(RESET)"
	git fetch origin
	git checkout $(DEV_BRANCH)
	git pull origin $(DEV_BRANCH)
	git checkout -b "release/week-$(WEEK)"
	@echo "$(GREEN)✔ Branch: release/week-$(WEEK)$(RESET)"
	@echo "$(YELLOW)Run make week$(WEEK) to check this week's gate$(RESET)"

branch-chore: ## Create a chore branch: make branch-chore NAME=update-deps
	@$(MAKE) _branch-create PREFIX="chore/$(NAME)"

branch-docs: ## Create a docs branch: make branch-docs NAME=update-screen-spec
	@$(MAKE) _branch-create PREFIX="docs/$(NAME)"

# Internal helper
_branch-create:
	@if [ -z "$(NAME)" ] || [ "$(NAME)" = "feature" ]; then \
		echo "$(RED)✗ NAME and TICKET are required. Example: make branch-feature TICKET=W1-03 NAME=theme-setup$(RESET)"; \
		exit 1; \
	fi
	git fetch origin
	git checkout $(DEV_BRANCH) 2>/dev/null || git checkout -b $(DEV_BRANCH) origin/$(DEV_BRANCH)
	git pull origin $(DEV_BRANCH)
	git checkout -b "$(PREFIX)"
	@echo "$(GREEN)✔ Branch: $(PREFIX) (from $(DEV_BRANCH))$(RESET)"
	@echo "$(YELLOW)Push: git push -u origin $(PREFIX)$(RESET)"

merge-dev: ## Merge the current branch into develop (interactive)
	@BRANCH=$$(git rev-parse --abbrev-ref HEAD); \
	echo "$(YELLOW)▶ Merge '$$BRANCH' → $(DEV_BRANCH)$(RESET)"; \
	read -p "Continue? [y/N] " ans; \
	[ "$$ans" = "y" ] || exit 0; \
	$(MAKE) check || (echo "$(RED)✗ check failed. Fix it first.$(RESET)" && exit 1); \
	git checkout $(DEV_BRANCH) && git pull origin $(DEV_BRANCH) && \
	git merge --no-ff "$$BRANCH" -m "Merge $$BRANCH → $(DEV_BRANCH)" && \
	git push origin $(DEV_BRANCH) && \
	echo "$(GREEN)✔ Merged into $(DEV_BRANCH)$(RESET)"

merge-main: ## Merge develop into main (release or hotfix)
	@echo "$(RED)⚠ Merging into $(MAIN_BRANCH). Make sure QA has passed!$(RESET)"
	@read -p "Continue? [y/N] " ans; [ "$$ans" = "y" ] || exit 0
	git checkout $(MAIN_BRANCH) && git pull origin $(MAIN_BRANCH)
	git merge --no-ff $(DEV_BRANCH) -m "Release: merge $(DEV_BRANCH) → $(MAIN_BRANCH)"
	git push origin $(MAIN_BRANCH)
	@echo "$(GREEN)✔ $(MAIN_BRANCH) updated. Tag the version: git tag v0.x.0$(RESET)"

pr-ready: ## Check everything before a PR (lint, test, nav-check)
	@echo "$(BLUE)▶ PR readiness check…$(RESET)"
	@$(MAKE) check
	@$(MAKE) nav-check
	@echo ""
	@echo "$(GREEN)$(BOLD)✔ Ready for a PR!$(RESET) Branch: $$(git rev-parse --abbrev-ref HEAD)"
	@echo "$(CYAN)Commit summary:$(RESET)"
	@git log --oneline origin/$(DEV_BRANCH)..HEAD 2>/dev/null || git log --oneline -5


# =============================================================================
# WEEKLY GATES
# Based on IMPLEMENTATION_PLAN.md §2
# =============================================================================

week1: ## Week 1 gate: foundation and auth (debug build, unit tests)
	@echo "$(BOLD)$(CYAN)=== Week 1 gate: foundation and authentication ===$(RESET)"
	@echo "Checklist:"
	@echo "  [ ] W1-01: Metric definitions approved (manual check)"
	@echo "  [ ] W1-02: Debug build works → run: make build"
	@echo "  [ ] W1-03: Theme with light and dark previews"
	@echo "  [ ] W1-04: Five-tab navigation and back work"
	@echo "  [ ] W1-05: Backend health endpoint responds"
	@echo "  [ ] W1-06: Real register, login, and logout"
	@echo "  [ ] W1-07: S01 to S03 with error validation"
	@echo ""
	@$(MAKE) build
	@$(MAKE) test-unit
	@echo ""
	@echo "$(YELLOW)Gate passes when the build succeeds, tests pass, and one account can log in and out$(RESET)"

week2: ## Week 2 gate: full tracking (build, test, nav-check)
	@echo "$(BOLD)$(CYAN)=== Week 2 gate: full tracking ===$(RESET)"
	@echo "Checklist:"
	@echo "  [ ] W2-01: Create logs idempotently with client_request_id"
	@echo "  [ ] W2-02: Editor with draft restore"
	@echo "  [ ] W2-03: List, detail, filter, and pagination (S07 to S10)"
	@echo "  [ ] W2-04: Update and delete with 409 version conflicts"
	@echo "  [ ] W2-05: Dashboard aggregates update automatically"
	@echo "  [ ] W2-06: Data isolation between accounts"
	@echo ""
	@$(MAKE) check
	@$(MAKE) nav-check
	@echo "$(YELLOW)Gate passes with full CRUD, no data leaks between accounts, and a live dashboard$(RESET)"

week3: ## Week 3 gate: guides and statistics (check, nav graph)
	@echo "$(BOLD)$(CYAN)=== Week 3 gate: guides and evaluation ===$(RESET)"
	@echo "Checklist:"
	@echo "  [ ] W3-01: 12 to 20 guide articles reviewed"
	@echo "  [ ] W3-02: Guide search, list, detail, and empty results"
	@echo "  [ ] W3-03: Statistics with daily series, categories, and methods"
	@echo "  [ ] W3-04: Charts with text alternatives for 7 and 30 days"
	@echo "  [ ] W3-05: XP and level baseline"
	@echo "  [ ] W3-06: Streaks and achievements (once core is stable)"
	@echo ""
	@$(MAKE) check
	@$(MAKE) nav-html
	@echo "$(YELLOW)Gate passes when the login-to-statistics demo runs against the real server$(RESET)"

week4: ## Week 4 gate: resilience and demo (check, release build)
	@echo "$(BOLD)$(CYAN)=== Week 4 gate: resilience and demo ===$(RESET)"
	@echo "Checklist:"
	@echo "  [ ] W4-01: Regression tests with evidence"
	@echo "  [ ] W4-02: TalkBack, 200% font, and landscape"
	@echo "  [ ] W4-03: Demo build and staging config"
	@echo "  [ ] W4-04: Reminders (once core passes)"
	@echo "  [ ] W4-07: Demo recorded and final bug list logged"
	@echo ""
	@$(MAKE) check
	@$(MAKE) nav-check
	@$(MAKE) build-release 2>/dev/null || echo "$(YELLOW)The release build needs a signing config$(RESET)"
	@echo "$(YELLOW)Gate passes when the end-to-end demo is stable on the demo device$(RESET)"


# =============================================================================
# SETUP & TOOLS
# =============================================================================

setup: ## First-time setup: check tools, create develop, first build
	@echo "$(BOLD)$(CYAN)=== Setup WasteNot Android ===$(RESET)"
	@echo ""
	@echo "$(BLUE)▶ Checking prerequisites…$(RESET)"
	@command -v git  >/dev/null 2>&1 && echo "  $(GREEN)✔ git$(RESET)"   || echo "  $(RED)✗ git not found$(RESET)"
	@command -v java >/dev/null 2>&1 && echo "  $(GREEN)✔ java$(RESET)"  || echo "  $(RED)✗ java not found$(RESET)"
	@command -v adb  >/dev/null 2>&1 && echo "  $(GREEN)✔ adb$(RESET)"   || echo "  $(RED)✗ adb not found$(RESET)"
	@test -x $(GRADLEW)            && echo "  $(GREEN)✔ gradlew$(RESET)" || echo "  $(RED)✗ gradlew missing$(RESET)"
	@echo ""
	@echo "$(BLUE)▶ Setting up the branch structure…$(RESET)"
	@git branch -a 2>/dev/null | grep -q "$(DEV_BRANCH)" && \
		echo "  Branch $(DEV_BRANCH) already exists" || \
		(git checkout -b $(DEV_BRANCH) && echo "  $(GREEN)✔ Branch $(DEV_BRANCH) created$(RESET)")
	@echo ""
	@echo "$(BLUE)▶ Installing git hooks…$(RESET)"
	@$(MAKE) setup-hooks
	@echo ""
	@echo "$(BLUE)▶ First build…$(RESET)"
	@$(MAKE) build
	@echo ""
	@echo "$(GREEN)$(BOLD)✔ Setup complete!$(RESET)"
	@echo ""
	@echo "  Next steps:"
	@echo "  1. Install the Android Studio plugins:"
	@echo "     - Compose HotSwan (hot reload)"
	@echo "     - Compose Navigation Graph (navigation visualizer)"
	@echo "  2. Read $(BOLD)README.md$(RESET) and $(BOLD)docs/design-kit/$(RESET)"
	@echo "  3. Start with: $(YELLOW)make branch-feature TICKET=W1-03 NAME=theme-setup$(RESET)"

setup-hooks: ## Install a pre-commit hook that runs lint and tests
	@mkdir -p .git/hooks
	@printf '#!/bin/sh\n# WasteNot pre-commit hook\necho "▶ Pre-commit: lint + unit test..."\n./gradlew lintDebug test --daemon -q\nif [ $$? -ne 0 ]; then\n  echo "✗ Lint or tests failed. Commit aborted."\n  echo "  Run '"'"'make check'"'"' for details."\n  exit 1\nfi\necho "✔ Pre-commit check passed."\n' > .git/hooks/pre-commit
	@chmod +x .git/hooks/pre-commit
	@echo "$(GREEN)✔ Git pre-commit hook installed$(RESET)"

device-info: ## Show connected device details
	@echo "$(BOLD)Connected devices:$(RESET)"
	@adb devices -l
	@echo ""
	@if [ -n "$(DEVICE)" ]; then \
		echo "$(BOLD)Active device: $(DEVICE)$(RESET)"; \
		adb -s $(DEVICE) shell getprop ro.product.model 2>/dev/null | xargs -I{} echo "  Model  : {}"; \
		adb -s $(DEVICE) shell getprop ro.build.version.release 2>/dev/null | xargs -I{} echo "  Android: {}"; \
		adb -s $(DEVICE) shell wm size 2>/dev/null | xargs -I{} echo "  Screen : {}"; \
	else \
		echo "$(YELLOW)No active device. Connect a device with USB debugging enabled.$(RESET)"; \
	fi

screenshots: ## Capture screenshots from every connected device
	@echo "$(BLUE)▶ Capturing screenshots…$(RESET)"
	@mkdir -p screenshots
	@TIMESTAMP=$$(date +%Y%m%d_%H%M%S); \
	for DEV in $$(adb devices | awk 'NR>1 && $$2=="device"{print $$1}'); do \
		FILE="screenshots/$$DEV_$$TIMESTAMP.png"; \
		adb -s $$DEV exec-out screencap -p > "$$FILE" && \
		echo "  $(GREEN)✔ $$FILE$(RESET)"; \
	done


# =============================================================================
# DOCKER
# =============================================================================

docker-apk: ## Build the debug APK with unit tests in a container, output to dist/apk
	docker build --target apk --output type=local,dest=dist/apk .
	@echo "$(GREEN)✔ APK: dist/apk/$(RESET)"

docker-ci: ## Run the CI gate in a container without a local Android SDK
	docker build --target apk --output type=local,dest=dist/apk \
		--build-arg GRADLE_TASKS="lintDebug testDebugUnitTest assembleDebug assembleStaging" .
