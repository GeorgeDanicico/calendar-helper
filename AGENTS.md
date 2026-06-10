# Repository Guidelines

## Project Structure & Module Organization
This repository is a small Quarkus service built with Maven. Application code lives under `src/main/java/com/sharky/dg/calendar`; the `config` package contains OpenAI appointment extraction wiring, and Quarkus discovers beans without a hand-written application bootstrap class. Runtime configuration lives in `src/main/resources/application.yaml`. Static resources are served from `src/main/resources/META-INF/resources`. Tests belong under `src/test/java` using the same package structure as production code. Treat `target/` as generated output and do not edit or commit files from it.

## Build, Test, and Development Commands
Run commands from the repository root. This checkout's Maven wrapper is missing `.mvn/wrapper/maven-wrapper.properties`, so use the installed Maven command unless the wrapper metadata is restored.

- `mvn quarkus:dev` starts the app locally with Quarkus dev mode.
- `mvn test` runs the JUnit test suite.
- `mvn -DskipTests package` builds the executable Quarkus jar quickly.
- `mvn clean` removes generated build output when you need a fresh build.

## Coding Style & Naming Conventions
Follow the existing Java style in this repo: tabs for indentation, `UpperCamelCase` for classes and records, `lowerCamelCase` for methods and fields, and package names under `com.sharky.dg.calendar`. Use CDI scopes such as `@ApplicationScoped`, constructor injection with `@Inject`, and MicroProfile `@ConfigProperty` for configuration. Prefer immutable value types like records where appropriate. Keep YAML keys lowercase and nested logically, for example `app.openai.chat.system-prompt`.

## Testing Guidelines
This project uses JUnit 5 with Quarkus test support. Name test classes after the subject class with a `Tests` suffix, for example `OpenAiAppointmentExtractionClientTests`. Add focused unit tests for configuration and service behavior, and use `@QuarkusTest` for application wiring checks. Run `mvn test` before opening a pull request.

## Commit & Pull Request Guidelines
Follow a clear imperative commit style: `feat: add appointment extraction tests`, `fix: default blank system prompt`. Keep commits scoped to one change. Pull requests should include a short summary, testing notes, linked issue or task when relevant, and sample request/response details for behavior changes that affect API output or prompt configuration.

## Configuration Notes
Do not hardcode secrets in `application.yaml`. Supply OpenAI and Google credentials through environment variables or external configuration, and only keep safe defaults in tracked files.
