# Contributing Guide

This document describes the development workflow and coding standards for this repository. **All guidelines, code, comments, and documentation must be written in English.**

## Git Branching Strategy

- The main working branch is `develop`. All feature work and fixes should be committed to `develop` (or a feature branch created from it).
- Release/stable code lives in the default branch (`main`/`master`).

## Commit Message Convention

We follow the **Conventional Commits** specification. All commit messages **must be written in English**.

Format:

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
| `docs`     | Documentation only changes                                       |
| `style`    | Formatting, missing semicolons, etc. (no logic change)          |
| `refactor` | Code change that neither fixes a bug nor adds a feature         |
| `perf`     | Code change that improves performance                            |
| `test`     | Adding or fixing tests                                          |
| `build`    | Changes to build system or dependencies                          |
| `ci`       | Changes to CI configuration files and scripts                    |
| `chore`    | Other changes that don't modify src or test files               |
| `revert`   | Reverts a previous commit                                       |

### Breaking changes

Append `!` after the type/scope (e.g. `refactor(ui)!: ...`) or include `BREAKING CHANGE:` in the footer.

### Examples

```
feat(ui): add toggle switch component
fix(layout): prevent overflow on narrow containers
docs(readme): update installation instructions
refactor(core)!: rename Component API surface methods
```

## Coding Standards

- **All source code identifiers must be in English** (classes, methods, variables, packages).
- **All code comments must be in English** (Javadoc, inline comments, TODOs).
- **All documentation must be in English** (README, docs folder, commit messages, PR descriptions, issue titles/bodies).

## GitHub Configuration

Local Git configuration required before committing:

```bash
git config user.name  "yasmramos"
git config user.email "yasmramos95@gmail.com"
```

Authentication uses HTTPS with a personal access token stored via the Git credential helper.
