# Commit and Code Standards

## Language
- All code, comments, and documentation must be written in **English**.

## Commit Message Format
We follow the **Conventional Commits** specification:

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types
- `feat`: A new feature
- `fix`: A bug fix
- `docs`: Documentation changes
- `style`: Formatting, missing semicolons, etc. (no code change)
- `refactor`: Code restructuring (no feature/fix)
- `perf`: Performance improvements
- `test`: Adding or updating tests
- `chore`: Build process, tooling, CI configs
- `ci`: CI/CD configuration changes
- `revert`: Reverts a previous commit

### Rules
1. Subject line: max 50 characters, imperative mood, lowercase, no period at end.
2. Body (optional): max 72 characters per line, explain *what* and *why*.
3. Footer (optional): reference issues (`Closes #123`) or breaking changes (`BREAKING CHANGE:`).

### Examples
```
feat(button): add loading state to primary button
fix(grid): resolve column alignment issue on mobile
docs(readme): update installation instructions
```
