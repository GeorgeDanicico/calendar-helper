# Repository Guidelines

## Project Structure & Module Organization
This repository is a small Spring Boot 4 service built with Maven. Application code lives under `src/main/java/com/sharky/dg/calendar`; the current `config` package contains OpenAI chat wiring, and `CalendarApplication.java` is the bootstrap entry point. Runtime configuration lives in `src/main/resources/application.yaml`. Tests belong under `src/test/java` using the same package structure as production code. Treat `target/` as generated output and do not edit or commit files from it.

## Build, Test, and Development Commands
Use the Maven wrapper so local Maven installation details do not matter.

- `./mvnw spring-boot:run` starts the app locally.
- `./mvnw test` runs the JUnit test suite.
- `./mvnw -DskipTests package` builds the executable jar quickly.
- `./mvnw clean` removes generated build output when you need a fresh build.

Run commands from the repository root.

## Coding Style & Naming Conventions
Follow the existing Java style in this repo: tabs for indentation, `UpperCamelCase` for classes and records, `lowerCamelCase` for methods and fields, and package names under `com.sharky.dg.calendar`. Keep Spring configuration in focused classes such as `OpenAiChatConfiguration`. Prefer constructor injection and immutable value types like records where appropriate. Keep YAML keys lowercase and nested logically, for example `app.openai.chat.system-prompt`.

## Testing Guidelines
This project uses JUnit 5 with Spring Boot test support. Name test classes after the subject class with a `Tests` suffix, for example `OpenAiChatClientFactoryTests`. Add focused unit tests for configuration and factory behavior, and reserve `@SpringBootTest` for application wiring checks. Run `./mvnw test` before opening a pull request.

## Commit & Pull Request Guidelines
Git history is not available in this workspace, so follow a clear imperative commit style: `feat: add chat client factory tests`, `fix: default blank system prompt`. Keep commits scoped to one change. Pull requests should include a short summary, testing notes, linked issue or task when relevant, and sample request/response details for behavior changes that affect API output or prompt configuration.

## Configuration Notes
Do not hardcode secrets in `application.yaml`. Supply OpenAI credentials through environment variables or external configuration, and only keep safe defaults in tracked files.
