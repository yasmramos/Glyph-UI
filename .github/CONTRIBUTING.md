# Contributing to Glyph UI

Thank you for your interest in contributing to Glyph UI! Please read this guide before submitting changes.

## Language Policy

- **All source code, identifiers, comments, and documentation must be written in English.** This keeps the project accessible to a global audience and ensures consistent tooling support.
- Commit messages must also be written in English.

## Git Branching Model

- `main` — stable, release-ready code only.
- `develop` — active integration branch. **All feature work and pull requests target `develop`.**
- Create feature branches from `develop` using a short, descriptive name: `feat/<short-topic>` or `fix/<short-topic>`.

## Commit Message Convention

We follow the **Conventional Commits** specification (https://www.conventionalcommits.org):

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

### Allowed types

| Type       | Purpose                                                        |
|------------|----------------------------------------------------------------|
| `feat`     | A new feature                                                  |
| `fix`      | A bug fix                                                      |
| `docs`     | Documentation-only changes                                     |
| `style`    | Formatting, missing semicolons, etc. (no logic change)         |
| `refactor` | Code change that neither fixes a bug nor adds a feature        |
| `perf`     | Code change that improves performance                          |
| `test`     | Adding or correcting tests                                     |
| `build`    | Changes affecting the build system or dependencies (e.g. Maven) |
| `ci`       | Changes to CI configuration files and scripts                  |
| `chore`    | Routine maintenance tasks                                      |
| `revert`   | Reverts a previous commit                                      |

### Rules

- Use the imperative mood ("add", not "added" / "adds").
- Description must start in lowercase and end without a period.
- Keep the subject line under 72 characters.
- Breaking changes must be marked with `!` after the type/scope (e.g. `feat(api)!: remove legacy renderer`) or a `BREAKING CHANGE:` footer.

### Examples

```
feat(layout): add flexbox gap support
fix(renderer): prevent crash when canvas size is zero
docs(readme): document macOS -XstartOnFirstThread requirement
refactor(core): extract event dispatch into EventRouter
```

## Development Workflow

1. Ensure your local `develop` branch is up to date: `git checkout develop && git pull`.
2. Create a feature branch from `develop`.
3. Write code and tests **in English** (including comments and Javadoc).
4. Build and verify locally: `mvn clean verify`.
5. Commit using Conventional Commits format.
6. Push your branch and open a pull request against `develop`.

## Code Style

- Java 17+ source level.
- Follow existing formatting conventions in the repository.
- Public APIs must have Javadoc (in English).
- Avoid dead code and unused imports.

## API Boundary (internal packages)

The public API of this library is defined by a package whitelist — see
[`docs/architecture/internal-boundary.md`](../docs/architecture/internal-boundary.md).

- Any package containing a segment named `internal` (e.g.
  `com.glyphui.core.internal`) is **NOT public API**: its types may change or
  disappear in any release, including patch releases.
- Placement rule: **if it isn't needed by an application author, it goes in
  `com.glyphui.core.internal`.** This includes test hooks/shims and classes
  that only exist to wire internal subsystems together.
- `com.glyphui.core.backend` is a supported extension point (SPI): new window
  backends are welcome there, but ad-hoc test code does not belong in it.
- Do not add new public types to whitelisted packages unless they are a
  deliberate API addition (call this out in the PR description).

## Reporting Issues

When opening an issue, include:

- A clear, descriptive title.
- Steps to reproduce the problem.
- Expected vs. actual behavior.
- Environment details (OS, Java version, Maven version).
