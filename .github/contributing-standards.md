# Project Working Standards

These standards apply to all work in this repository.

## Language

- All source code, identifiers, comments, Javadoc, commit messages, and
  documentation must be written in **English**.

## Git Branching

- Day-to-day development happens on the `develop` branch.
- `main` holds only stable, release-ready code.
- Feature work should branch off `develop` and be merged back into `develop`.

## Commit Messages (Conventional Commits)

All commit messages must follow the Conventional Commits specification:

```
<type>(<optional scope>): <description in English, imperative mood>

[optional body]

[optional footer]
```

Allowed types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`,
`build`, `ci`, `chore`, `revert`.

Examples:

- `feat(button): add hover state animation`
- `fix(layout): correct child panel coordinate offset`
- `docs(readme): document macOS -XstartOnFirstThread requirement`

Rules:
- Description line must be 72 characters or fewer and must not end with a period.
- Use the imperative mood ("add", not "added"/"adds").
- One logical change per commit.
