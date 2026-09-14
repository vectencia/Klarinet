# Contributing to Klarinet

Thank you for your interest in contributing to Klarinet! This document provides guidelines and instructions for contributing.

## Development Setup

### Prerequisites

- **JDK 17** (Temurin recommended)
- **Android SDK** with API level 24+ and NDK installed
- **Xcode 15+** (for iOS/macOS/watchOS/tvOS targets, macOS only)
- **[Zig](https://ziglang.org/)** on `PATH` (only for rebuilding Linux/Windows JVM natives)
- **Kotlin Multiplatform** plugin in your IDE (IntelliJ IDEA or Android Studio recommended)

`gradle.properties` sets `useDebugDependencies=true` so the Compose `:demo` module (and `:demo-android`) compile against this tree. Set `useDebugDependencies=false` to point `:demo` at Maven Central. `:demo-web`, `:demo-native`, and `:sample` always use `project(":klarinet")`.

### Clone and Build

```bash
git clone https://github.com/vectencia/Klarinet.git
cd Klarinet
```

## Build Commands

Build all modules:

```bash
./gradlew build
```

Build individual modules:

```bash
./gradlew :klarinet:build
./gradlew :klarinet-android:assembleDebug
./gradlew :klarinet:compileAndroidMain
./gradlew :klarinet:compileKotlinIosSimulatorArm64
./gradlew :klarinet:compileKotlinMacosArm64
./gradlew :klarinet:buildJvmNatives
./gradlew :demo-android:assembleDebug
./gradlew :demo-web:jsBrowserDevelopmentRun
./gradlew :sample:run
```

`buildJvmNatives` rebuilds the packaged JVM miniaudio libraries. Host macOS binaries use CMake + Clang. Linux and Windows binaries require [Zig](https://ziglang.org/) on `PATH`.

## Test Commands

Run all common tests:

```bash
./gradlew :klarinet:allTests
./gradlew :klarinet-coroutines:allTests
./gradlew :klarinet:dspTests
```

Run Android instrumented tests (requires emulator or device):

```bash
./gradlew :klarinet:connectedAndroidDeviceTest
```

Run iOS simulator tests (macOS only):

```bash
./gradlew :klarinet:iosSimulatorArm64Test
```

Run all tests:

```bash
./gradlew :klarinet:allTests :klarinet-coroutines:allTests :klarinet:dspTests :klarinet:iosSimulatorArm64Test
```

## Makefile Shortcuts

The project includes a `Makefile` for common commands:

| Command | Description |
|---|---|
| `make build` | Build all modules |
| `make klarinet` | Build the library only |
| `make demo` | Build the demo app (Android) |
| `make pages` | Assemble GitHub Pages tree into `public/` (demo + Dokka) |
| `make test` | Run `:klarinet:allTests` |
| `make test-coroutines` | Run `:klarinet-coroutines:allTests` |
| `make test-dsp` | Run C++ DSP tests |
| `make test-js` | Run JS browser tests (Chrome Headless) |
| `make test-android` | Run Android instrumented tests |
| `make test-ios` | Run iOS simulator tests |
| `make test-all` | Run Klarinet + coroutines + DSP + iOS simulator tests |
| `make clean` | Clean build artifacts |
| `make publish` | Publish to Maven Central |

## Project Structure

| Module | Description |
|---|---|
| `klarinet` | KMP audio SDK: common API + platform backends |
| `klarinet-android` | Android Oboe/C++ JNI backend (`com.android.library`) |
| `klarinet-coroutines` | Optional Flow and suspending extensions |
| `demo` | Shared Compose Multiplatform demo UI |
| `demo-android` | Android application entry point |
| `demo-web` | Browser demo (published at `/` on GitHub Pages) |
| `sample` | One-file JVM sine-wave sample |

## GitHub Pages

The production web demo and Dokka API docs are a GitHub Pages site built by [`.github/workflows/pages.yml`](.github/workflows/pages.yml). Maintainer details (site layout, org-admin enablement, `make pages`, CORS/mic, troubleshooting) are in [GITHUB_PAGES.md](GITHUB_PAGES.md).

PRs do not publish a preview URL. After merge to `main`, check the [Pages workflow](https://github.com/vectencia/Klarinet/actions/workflows/pages.yml). On this repo the site is live; on a fork, if the deploy step is skipped, `GET /repos/{owner}/{repo}/pages` failed (Pages not enabled) — a repo admin must set **Settings → Pages → Source** to **GitHub Actions**.

When a PR changes `:demo-web`, KDoc that Dokka renders, or the Pages workflow/assemble script, include a local Pages preview in the test plan:

```bash
make pages
python3 -m http.server 8080 --directory public
```

Confirm the demo at `http://localhost:8080/` and API docs at `http://localhost:8080/api/`. Keep demo asset URLs relative; the live site is served under `/Klarinet/`.

## Pull Request Guidelines

1. **Fork** the repository and create a feature branch from `main`.
2. **Write tests** for any new functionality.
3. **Follow existing code style** -- the project uses standard Kotlin conventions.
4. **Keep PRs focused** -- one feature or fix per pull request.
5. **Update documentation** if your change affects the public API.
6. **Run tests locally** before submitting:
   ```bash
   ./gradlew :klarinet:allTests :klarinet-coroutines:allTests :klarinet:dspTests
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
- Security reports: see [SECURITY.md](SECURITY.md).
- Conduct: see [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## License

By contributing to Klarinet, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
