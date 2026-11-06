#!/usr/bin/env python3
"""Link a JVM cdylib with zig cc and keep the published C ABI exported.

rustc's cdylib version script hides every symbol it did not compile.
Zig 0.17 rejects --export-dynamic-symbol, so this wrapper adds the same
names to that script before invoking zig. KLARINET_ZIG_TARGET is the zig
triple, for example x86_64-linux-gnu.
"""

import os
import sys

PUBLISHED = (
    "klarinet_chain_add",
    "klarinet_chain_clear",
    "klarinet_chain_create",
    "klarinet_chain_destroy",
    "klarinet_chain_enqueue_param",
    "klarinet_chain_get_effect_count",
    "klarinet_chain_prepare",
    "klarinet_chain_process",
    "klarinet_chain_remove",
    "klarinet_create_effect",
    "klarinet_effect_destroy",
    "klarinet_effect_get_parameter",
    "klarinet_effect_get_type",
    "klarinet_effect_is_enabled",
    "klarinet_effect_prepare",
    "klarinet_effect_reset",
    "klarinet_effect_set_enabled",
    "klarinet_effect_set_parameter",
    "klarinet_offload_create",
    "klarinet_offload_destroy",
    "klarinet_offload_process",
    "klarinet_offload_set_xrun_callback",
)


def publish_version_script(path: str) -> None:
    with open(path, encoding="utf-8") as handle:
        text = handle.read()
    if "global:" not in text:
        return
    extra = "".join(f"    {name};\n" for name in PUBLISHED if f"{name};" not in text)
    if not extra:
        return
    text = text.replace("global:", "global:\n" + extra, 1)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(text)


def publish(args: list[str]) -> list[str]:
    prefix = "-Wl,--version-script="
    rewritten: list[str] = []
    for arg in args:
        if arg.startswith(prefix):
            publish_version_script(arg[len(prefix) :])
            rewritten.append(arg)
            continue
        if arg.startswith("-Wl,") and arg.endswith(".def"):
            # Zig rejects `-Wl,<path>.def`. GNU ld accepts the file as an input.
            # The MinGW DLL exported JNI names only, and rustc's .def already
            # lists those, so the C API stays inside the DLL.
            rewritten.append(arg[len("-Wl,") :])
            continue
        rewritten.append(arg)
    return rewritten


def linker_args(args: list[str], zig_target: str) -> list[str]:
    # rustc passes this aarch64 workaround. Zig 0.17's linker rejects it.
    dropped = "--fix-cortex-a53-843419"
    kept = [arg for arg in args if dropped not in arg]
    if "windows" not in zig_target:
        return kept
    # rustc's gnu link line uses -nodefaultlibs and then names MinGW
    # libraries Zig 0.17 does not search. Drop that pair and let zig cc
    # supply its own C runtime.
    skip = {"-nodefaultlibs", "-l:libpthread.a", "-lmsvcrt"}
    return [arg for arg in kept if arg not in skip]


def main() -> None:
    zig_target = os.environ.get("KLARINET_ZIG_TARGET", "")
    if not zig_target:
        sys.stderr.write("KLARINET_ZIG_TARGET is not set\n")
        sys.exit(1)
    args = publish(linker_args(sys.argv[1:], zig_target))
    os.execvp("zig", ["zig", "cc", "-target", zig_target, *args])


if __name__ == "__main__":
    main()
