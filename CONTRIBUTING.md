# Contributing to Koboe

Thank you for your interest in contributing to Koboe! This document provides guidelines and instructions for contributing.

## Development Setup

### Prerequisites

- **JDK 17** (Temurin recommended)
- **Android SDK** with API level 24+ and NDK installed
- **Xcode 15+** (for iOS/macOS targets, macOS only)
- **Kotlin Multiplatform** plugin in your IDE (IntelliJ IDEA or Android Studio recommended)

### Clone and Build

```bash
git clone https://github.com/vectencia/koboe.git
cd koboe
```

## Build Commands

Build all modules:

```bash
./gradlew build
```

Build individual modules:

```bash
./gradlew :koboe:build
./gradlew :koboe:assembleDebug
./gradlew :koboe:compileKotlinIosSimulatorArm64
./gradlew :koboe:compileKotlinMacosArm64
./gradlew :demo:assembleDebug
```

## Test Commands

Run all common tests:

```bash
./gradlew :koboe:allTests
```

Run Android instrumented tests (requires emulator or device):

```bash
./gradlew :koboe:connectedDebugAndroidTest
```

Run iOS simulator tests (macOS only):

```bash
./gradlew :koboe:iosSimulatorArm64Test
```

Run all tests:

```bash
./gradlew :koboe:allTests :koboe:iosSimulatorArm64Test
```

## Project Structure

| Module | Description |
|---|---|
| `koboe` | KMP audio SDK: common API + Android and Apple backends |
| `demo` | Demo application |

## Pull Request Guidelines

1. **Fork** the repository and create a feature branch from `main`.
2. **Write tests** for any new functionality.
3. **Follow existing code style** -- the project uses standard Kotlin conventions.
4. **Keep PRs focused** -- one feature or fix per pull request.
5. **Update documentation** if your change affects the public API.
6. **Run tests locally** before submitting:
   ```bash
   ./gradlew :koboe:allTests
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

By contributing to Koboe, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
