# Local development using Docker Compose

Copy the example env file and provide secrets:

```bash
cp .env.example .env
# edit .env and set a secure POSTGRES_PASSWORD
```

Start the database and Adminer:

```bash
docker compose up -d
```

Stop and remove containers and volumes:

```bash
docker compose down -v
```

View logs:

```bash
docker compose logs -f db adminer
```

Services

| Service | URL | Description |
|---|---|---|
| Adminer | http://localhost:8085 | Database GUI for Postgres (port from `ADMINER_PORT` in `.env`) |
| Spring Boot app | http://localhost:8080 | The application running on port 8080 |

## Conventional commits, branch naming, and changelog

Install tracked Git hooks:

```bash
./scripts/install-git-hooks.sh
```

What gets enforced:

- Commit messages must match Conventional Commits (`type(scope): description`).
- Branch names must match a similar format (`<type>/<description>` or `<type>/<scope>/<description>`).
- `./gradlew check` runs on commit-message validation for all types except `wip`.
- Allowed types: `build`, `chore`, `ci`, `docs`, `feat`, `fix`, `perf`, `refactor`, `revert`, `style`, `test`, `wip`

Examples:

```text
feat(auth): add token refresh endpoint
wip(auth): spike token refresh flow
fix/null-pointer-in-user-service
refactor/payments/remove-legacy-adapter
wip/prototype/new-login-flow
```

Generate changelog from conventional commits:

```bash
./scripts/generate-changelog.sh
```

Optional range/output controls:

```bash
./scripts/generate-changelog.sh --from v0.1.0 --to HEAD --output CHANGELOG.md
```
