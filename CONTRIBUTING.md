# Contributing

Thank you for helping improve WasteNot Android. This guide covers the workflow and standards every change is expected to follow.

## Workflow

1. Branch from `main` with a descriptive prefix: `feat/`, `fix/`, `refactor/`, `docs/`, or `chore/`.
2. Keep each commit to one logical change. Write a single-line message in the [Conventional Commits](https://www.conventionalcommits.org/) format, for example `fix(sync): keep local edits during a conflicting pull`.
3. Run `make check` before pushing, and `make pr-ready` before opening a pull request.
4. Open a pull request against `main`. Android CI must pass before merging.

## Standards

- **Specifications.** Follow the [design kit](docs/design-kit/README.md) and [AGENTS.md](AGENTS.md).
  - Take design tokens from `DESIGN.md`; never hardcode colors.
  - Put all user-facing text in `strings.xml`, in Indonesian.
- **Architecture.**
  - Keep a single `:app` module organized by feature, with ViewModel and StateFlow and unidirectional data flow.
  - Do not add generic base classes or one use case per endpoint.
- **Writes.** Every write sends a `client_request_id`. Never show success before the server confirms it.
- **Privacy.** Never log tokens, passwords, or personal payloads. Remove scan photos when the flow ends.
- **Room schema.** Every schema change needs a migration that preserves the sync queue.
- **Comments.** Comment only on what the code cannot express.

## Testing

- **Host checks.** Unit tests, Room tests, and builds run on the host without a device.
- **Device checks.** Use a physical device, never an emulator, and only with the owner's approval. If a device check was not run, say so in the pull request.
- **Evidence.** Build and unit test results do not replace a screenshot comparison with the design kit for UI changes.

## Security

Never commit keystores, signing passwords, `local.properties`, or API tokens. Release signing values live only in the CI secret store.
