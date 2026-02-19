# AGENTS.md - CalSync Development Guide

This file provides guidelines for agentic coding agents working on the CalSync project.

## Project Overview

CalSync is a universal calendar sync engine that mirrors "busy" time slots across multiple Google Calendars and CalDAV calendars. Built with Kotlin/JVM 21+ using Gradle as the build tool.

## Build Commands

```bash
# Build project (compiles + runs tests)
./gradlew build

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "rocks.jimi.calsync.cli.InputReaderTest"

# Run a single test method
./gradlew test --tests "rocks.jimi.calsync.cli.InputReaderTest.readLine returns input when no validation required"

# Create executable JAR (includes all dependencies)
./gradlew fatJar

# Create regular JAR
./gradlew jar

# Clean build directory
./gradlew clean

# Compile without testing
./gradlew assemble
```

## Project Structure

- **Root package** (`rocks.jimi.calsync`): files directly in `src/main/kotlin/`
- **Subpackages**: directories under `src/main/kotlin/` (e.g., `cli/`, `api/`, `config/`, `sync/`, `oauth/`)
- **Tests**: mirror main structure in `src/test/kotlin/`

## Code Style Guidelines

### General Conventions

- Language: Kotlin, JVM Target: 21
- Use Kotlin stdlib and kotlinx libraries over Java alternatives
- Prefer nullable types (`?`) and safe calls (`?.`) when null is valid
- Use `runBlocking` for bridging coroutine and blocking code in main entry points

### Naming Conventions

- Classes/Packages: PascalCase (e.g., `SyncEngine`, `InputReader`)
- Functions/Properties: camelCase (e.g., `runSync`, `safeGoogleCall`)
- Test classes: Append `Test` suffix (e.g., `InputReaderTest`)
- Test methods: Use backtick syntax (e.g., `` `readLine returns trimmed input` ``)
- Constants: UPPER_SNAKE_CASE (e.g., `DEFAULT_INTERVAL`)

### Import Organization

Order imports as follows (no blank lines between groups):
1. Kotlin standard library (`kotlin.*`)
2. kotlinx libraries (`kotlinx.*`)
3. Third-party libraries (external Maven packages)
4. Project internal imports (`rocks.jimi.calsync.*`)

### Types and Serialization

- Use `@Serializable` on all data classes for JSON/YAML serialization
- Use kotlinx.serialization (`Json`) for config and state - prefer over SnakeYAML
- Use Kotlin data classes for immutable DTOs and config models
- Prefer `val` over `var`; use `var` only when mutation is necessary

Example:
```kotlin
@Serializable
data class CalendarConfig(
    val id: String,
    val type: String,
    val calendarId: String? = null,
    val url: String? = null,
    val username: String? = null,
    val password: String? = null,
    val tokenFile: String = ""
)
```

### Error Handling

- Use try-catch blocks for operations that may fail (I/O, network calls)
- Catch specific exception types before general ones
- For Google API calls, handle `GoogleJsonResponseException` for API-specific errors
- Use `println` for CLI output, SLF4J for structured logging

### Coroutines Usage

- Use suspend functions for async operations (network, I/O)
- Wrap coroutine entry points with `runBlocking { }`
- Use `coroutineScope` or `SupervisorScope` for parallel operations when needed

### Testing

- Use `kotlin.test` framework (JUnit under the hood)
- Place tests in `src/test/kotlin/` mirroring main source structure
- Use dependency injection for testability (pass dependencies as constructor parameters)
- Test one thing per test method; use descriptive backtick names
- Use test doubles (mocks/stubs) for external dependencies

### CLI and User Interaction

- Use Clikt for CLI argument parsing (`com.github.ajalt.clikt:clikt`)
- Follow existing command structure: main command with subcommands
- Use `echo()` for user output, `echo(..., err = true)` for errors

### Configuration

- Store configuration in `config.yaml` using YAML format
- Use kotlinx.serialization for YAML parsing (preferred over SnakeYAML)
- Never commit secrets (OAuth tokens, credentials) to version control

## Key Dependencies

- Kotlin 2.3.10, Ktor Client 3.4.0, kotlinx.serialization 1.10.0
- kotlinx.coroutines 1.10.2, Google API Client 2.2.0, Google Calendar API v3-rev20231123-2.0.0
- Google Auth Library 1.20.0, JavaMail 1.6.2, Clikt 5.1.0, SLF4J Simple 2.0.9

## Gitignore Patterns

- `tokens/` - OAuth tokens
- `config.yaml` - User credentials
- `sync-state.json` - Runtime sync state
- `build/`, `*.jar`, `.gradle/`

## Running the Application

```bash
./gradlew fatJar
java -jar build/libs/calsync.jar configure
java -jar build/libs/calsync.jar sync   # Run once
java -jar build/libs/calsync.jar run    # Run as daemon
```
