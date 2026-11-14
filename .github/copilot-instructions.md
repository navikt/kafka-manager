# Copilot Instructions for kafka-manager

<!-- This file captures repository-specific context.
     Nav-wide language and framework conventions are provided by installed Copilot instructions. -->

## Repository Overview

Kafka Manager er en Nav-app som lar team lese meldinger fra Kafka-topics, vise consumer offsets og endre eller slette meldinger. Den støtter både on-prem Kafka og Aiven Kafka. Backend ligger i `api/`; frontend bygges fra `web-app/` og pakkes som statiske ressurser i backendens Spring Boot-jar.

## Tech Stack

- Backend: Kotlin, Spring Boot og Java 25, bygd med Gradle.
- Frontend: React, TypeScript og Vite; Gradle laster ned Node.js 24.22.2 og pnpm 12.15.0 for frontend-byggingen.
- `./gradlew build` bygger prosjektet. `./gradlew check` kjører backend-sjekker samt frontend-tester og lint.

## Key Patterns

- Topicene og deres plassering (`ON_PREM` eller `AIVEN`) samt key- og value-deserialisering konfigureres i `APP_CONFIG_JSON`. README-en oppgir støtte for STRING, DOUBLE, FLOAT, INTEGER, LONG, SHORT, UUID og AVRO.
- Tilgang til appen styres med Azure application group claims; oppgi en Azure AD-gruppe som bare inneholder brukere som skal ha tilgang.
- Gi også appen Kafka-tilgang til hvert konfigurert topic: systembruker som `CONSUMER` for on-prem, eller `read`-tilgang via Aiven ACL.
- README-en krever on-prem broker-URL, og schema registry-URL for on-prem-topics som bruker Avro. For Aiven konfigureres Kafka-pool ved deploy.

## Minimal Editing

When fixing a bug or implementing a feature, change only what is necessary.
Do not rename variables, restructure working code, or refactor beyond the task at hand.
Keep diffs small and focused so they are easy to review.
