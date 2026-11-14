# AGENTS.md — kafka-manager

Kafka Manager er en Nav-app for å lese meldinger fra Kafka-topics, vise consumer offsets og endre eller slette meldinger. Den støtter on-prem Kafka og Aiven Kafka.

## Build & Test Commands

```bash
./gradlew build
./gradlew check
```

Bygget bruker Java 25. `check` kjører også frontend-tester og lint.

## Project Structure

```text
api/       Kotlin- og Spring Boot-backend
web-app/   React/TypeScript-frontend
gradle/    Gradle-wrapper og versjonskatalog
```

## Code Style

### Minimal Editing

When fixing a bug or implementing a feature, change only what is necessary.
Do not rename variables, restructure working code, or refactor beyond the task at hand.
Keep diffs small and focused so they are easy to review.

## Git Workflow

Repoet dokumenterer ikke en særskilt Git-arbeidsflyt. Bidragsytere følger teamets praksis.

## Boundaries

### ✅ Always

- Run tests after changes
- Follow existing code patterns in the project
- Preserve existing code structure — do not reorganize or refactor beyond the task
- Validate all external input

### ⚠️ Ask First

- Changing authentication mechanisms
- Adding new dependencies
- Modifying database schema

### 🚫 Never

- Commit secrets or credentials
- Skip input validation on external boundaries
