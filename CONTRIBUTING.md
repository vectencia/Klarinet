# Contributing to Klarinet

Thank you for your interest in contributing to Klarinet! This document provides guidelines and instructions for contributing.

## Development Setup

### Prerequisites

- **JDK 17** (Temurin recommended)
- **Android SDK** with API level 24+ and NDK installed
- **Xcode 15+** (for iOS/macOS targets, macOS only)
- **Kotlin Multiplatform** plugin in your IDE (IntelliJ IDEA or Android Studio recommended)

### Clone and Build

```bash
git clone https://github.com/vectencia/klarinet.git
cd klarinet
```

## Build Commands

Build all modules:

```bash
./gradlew build
```

Build individual modules:

```bash
./gradlew :klarinet:build
./gradlew :klarinet:assembleDebug
./gradlew :klarinet:compileKotlinIosSimulatorArm64
./gradlew :klarinet:compileKotlinMacosArm64
./gradlew :demo:assembleDebug
```

## Test Commands

Run all common tests:

```bash
./gradlew :klarinet:allTests
```

Run Android instrumented tests (requires emulator or device):

```bash
./gradlew :klarinet:connectedDebugAndroidTest
```

Run iOS simulator tests (macOS only):

```bash
./gradlew :klarinet:iosSimulatorArm64Test
```

Run all tests:

```bash
./gradlew :klarinet:allTests :klarinet:iosSimulatorArm64Test
```

## Makefile Shortcuts

The project includes a `Makefile` for common commands:

| Command | Description |
|---|---|
| `make build` | Build all modules |
| `make klarinet` | Build the library only |
| `make demo` | Build the demo app (Android) |
| `make test` | Run common tests |
| `make test-android` | Run Android instrumented tests |
| `make test-ios` | Run iOS simulator tests |
| `make test-all` | Run all tests across platforms |
| `make clean` | Clean build artifacts |
| `make publish` | Publish to Maven Central |

## Project Structure

| Module | Description |
|---|---|
| `klarinet` | KMP audio SDK: common API + Android and Apple backends |
| `demo` | Demo application |

## Pull Request Guidelines

1. **Fork** the repository and create a feature branch from `main`.
2. **Write tests** for any new functionality.
3. **Follow existing code style** -- the project uses standard Kotlin conventions.
4. **Keep PRs focused** -- one feature or fix per pull request.
5. **Update documentation** if your change affects the public API.
6. **Run tests locally** before submitting:
   ```bash
   ./gradlew :klarinet:allTests
   ```
7. **Write a clear PR description** explaining what changed and why.

## Commit Messages

Use clear, descriptive commit messages. Prefer the imperative mood:

- "Add audio buffer pooling for reduced allocations"
- "Fix sample rate conversion on iOS 16"
- "Update Kotlin to 2.1.x"

## Reporting Issues

- Use GitHub Issues to report bugs or request features.
- Include steps to reproduce, expected behavior, and actual behavior.
- Specify the platform (Android API level, iOS version, macOS version) and device.

## License

By contributing to Klarinet, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
