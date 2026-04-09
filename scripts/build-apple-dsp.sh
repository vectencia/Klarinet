#!/usr/bin/env bash
#
# Builds libklarinet-dsp.a for every Apple target that Kotlin/Native requires.
# Usage: ./scripts/build-apple-dsp.sh [--clean]
#
# The resulting static libraries are placed under:
#   klarinet/build/dsp/<target>/libklarinet-dsp.a
#
# Targets:
#   iosArm64            -> arm64,  iPhoneOS SDK
#   iosSimulatorArm64   -> arm64,  iPhoneSimulator SDK
#   iosX64              -> x86_64, iPhoneSimulator SDK
#   macosArm64          -> arm64,  MacOSX SDK
#   macosX64            -> x86_64, MacOSX SDK
#   tvosArm64           -> arm64,  AppleTVOS SDK
#   tvosSimulatorArm64  -> arm64,  AppleTVSimulator SDK

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DSP_SRC="$PROJECT_ROOT/klarinet/src/cpp/dsp"
BUILD_ROOT="$PROJECT_ROOT/klarinet/build/dsp"

if [[ "${1:-}" == "--clean" ]]; then
    echo "Cleaning DSP build artifacts..."
    rm -rf "$BUILD_ROOT"
fi

# Each entry: "<kotlin_target>|<cmake_osx_sysroot>|<cmake_osx_arch>|<deployment_target_var>|<deployment_target_value>"
TARGETS=(
    "iosArm64|iphoneos|arm64|CMAKE_OSX_DEPLOYMENT_TARGET|13.0"
    "iosSimulatorArm64|iphonesimulator|arm64|CMAKE_OSX_DEPLOYMENT_TARGET|13.0"
    "iosX64|iphonesimulator|x86_64|CMAKE_OSX_DEPLOYMENT_TARGET|13.0"
    "macosArm64|macosx|arm64|CMAKE_OSX_DEPLOYMENT_TARGET|11.0"
    "macosX64|macosx|x86_64|CMAKE_OSX_DEPLOYMENT_TARGET|11.0"
    "tvosArm64|appletvos|arm64|CMAKE_OSX_DEPLOYMENT_TARGET|13.0"
    "tvosSimulatorArm64|appletvsimulator|arm64|CMAKE_OSX_DEPLOYMENT_TARGET|13.0"
)

build_target() {
    local entry="$1"
    IFS='|' read -r kotlin_target sdk arch deploy_var deploy_val <<< "$entry"

    local build_dir="$BUILD_ROOT/$kotlin_target/build"
    local out_dir="$BUILD_ROOT/$kotlin_target"

    # Skip if already built (use --clean to force rebuild)
    if [[ -f "$out_dir/libklarinet-dsp.a" ]]; then
        echo "-- $kotlin_target: already built, skipping."
        return
    fi

    echo "-- Building $kotlin_target (sdk=$sdk, arch=$arch)..."

    local sysroot
    sysroot="$(xcrun --sdk "$sdk" --show-sdk-path)"

    mkdir -p "$build_dir"

    cmake -S "$DSP_SRC" -B "$build_dir" \
        -DCMAKE_SYSTEM_NAME=Darwin \
        -DCMAKE_OSX_SYSROOT="$sysroot" \
        -DCMAKE_OSX_ARCHITECTURES="$arch" \
        -D"$deploy_var"="$deploy_val" \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_C_COMPILER="$(xcrun --sdk "$sdk" --find clang)" \
        -DCMAKE_CXX_COMPILER="$(xcrun --sdk "$sdk" --find clang++)" \
        -G "Unix Makefiles" \
        > /dev/null 2>&1

    cmake --build "$build_dir" --config Release -j "$(sysctl -n hw.logicalcpu)" \
        > /dev/null 2>&1

    # Copy the static library to the output directory
    cp "$build_dir/libklarinet-dsp.a" "$out_dir/libklarinet-dsp.a"

    echo "-- $kotlin_target: done -> $out_dir/libklarinet-dsp.a"
}

echo "=== Building klarinet-dsp for Apple targets ==="
echo "DSP source: $DSP_SRC"
echo ""

for target in "${TARGETS[@]}"; do
    build_target "$target"
done

echo ""
echo "=== All Apple DSP builds complete ==="
