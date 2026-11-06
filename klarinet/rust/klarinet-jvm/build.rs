/// Symbols the C JVM dylib exported from `klarinet_dsp.cpp` and
/// `AudioCallbackOffload.cpp`. The cdylib link keeps them, and these flags
/// publish them again. `klarinet_fx_*` and `klarinet_echain_*` stay exported
/// from the Rust rlib. `klarinet_ma_*` stays hidden inside the wrapper.
const PUBLISHED_C_API: &[&str] = &[
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
];

fn publish_c_api(target: &str) {
    if !target.contains("apple") {
        // ELF cdylibs export these through the zig linker wrapper, which
        // edits rustc's version script. Zig rejects --export-dynamic-symbol.
        return;
    }
    for name in PUBLISHED_C_API {
        println!("cargo:rustc-cdylib-link-arg=-Wl,-exported_symbol,_{name}");
    }
}

fn main() {
    println!("cargo:rerun-if-env-changed=KLARINET_LINK_ARCHIVES");
    println!("cargo:rerun-if-changed=build.rs");
    let archives = std::env::var("KLARINET_LINK_ARCHIVES")
        .expect("KLARINET_LINK_ARCHIVES must list static libraries separated by |");
    let target = std::env::var("TARGET").unwrap_or_default();
    for archive in archives.split('|') {
        let archive = archive.trim();
        if archive.is_empty() {
            continue;
        }
        println!("cargo:rerun-if-changed={archive}");
        if target.contains("apple") {
            println!("cargo:rustc-link-arg=-Wl,-force_load,{archive}");
        } else {
            println!("cargo:rustc-link-arg=-Wl,--whole-archive");
            println!("cargo:rustc-link-arg={archive}");
            println!("cargo:rustc-link-arg=-Wl,--no-whole-archive");
        }
    }
    publish_c_api(&target);
    if target.contains("apple") {
        println!("cargo:rustc-link-lib=framework=CoreAudio");
        println!("cargo:rustc-link-lib=framework=AudioUnit");
        println!("cargo:rustc-link-lib=framework=CoreFoundation");
        println!("cargo:rustc-link-arg=-lc++");
    } else if target.contains("windows") {
        println!("cargo:rustc-link-lib=dylib=ole32");
        println!("cargo:rustc-link-lib=dylib=winmm");
        println!("cargo:rustc-link-arg=-lc++");
    } else if target.contains("linux") {
        println!("cargo:rustc-link-lib=dylib=pthread");
        println!("cargo:rustc-link-lib=dylib=dl");
        println!("cargo:rustc-link-lib=dylib=m");
        println!("cargo:rustc-link-arg=-lc++");
    }
}
