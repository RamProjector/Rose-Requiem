# Local malware-scanning engine decision

## Evidence

YARA-X is a Rust rule compiler and scanning engine, not a bundled malware database. Its Rust API compiles YARA source into `Rules`, then scans files with `Scanner::scan_file`; it also exposes scan timeouts. The crate is BSD-3-Clause, suitable to evaluate for this GPL-3.0-only app. Current crate metadata reports Rust 1.95.0 as its minimum. [1] [2] [3]

YARA-X publishes prebuilt command-line binaries for Linux, macOS, and Windows, not Android. Its source build requires Rust, so Android packaging needs a native Android build path. `cargo-ndk` documents producing Android ABI `.so` libraries in Gradle's expected `jniLibs/<abi>` layout. [4] [5]

The ReversingLabs public YARA repository states MIT licensing and describes analyst-maintained detection rules. The inspected snapshot is commit `e0a0be54aa1e11ccfd6854e4f19e9476f328fd84`; it contains 310 `.yara` files (about 3.9 MB) and approximately 1,240 rule declarations. Its rules include `pe` and `elf` modules, and the inspected snapshot has no `include` directives or duplicate rule identifiers. [6]

ClamAV's `libclamav` is GPL-2.0-only; its official signature database is maintained through FreshClam/CVD updates, usually once or twice daily. The ClamAV docs do not list Android among tested platforms, and its documented recommended memory footprint is 3 GiB or more. Linking its GPL-2.0-only library into this GPL-3.0-only app also raises a license-compatibility problem. These factors make YARA-X plus a permissively licensed rule source the more appropriate candidate for an Android-native integration. [7] [8]

YARA rules can produce false positives and require ongoing quality checks. YARA Forge's documentation describes source attribution, filtering, quality scoring, performance checks, and known false-positive analysis, and notes that its source repositories have mixed licenses. Therefore, do not ingest an arbitrary aggregate rule bundle without reviewing its component licenses and provenance. [9]

## References

[1]: https://docs.rs/yara-x/latest/yara_x/ "YARA-X Rust crate API"
[2]: https://docs.rs/yara-x/latest/yara_x/struct.Scanner.html "YARA-X Scanner API"
[3]: https://crates.io/crates/yara-x "YARA-X crate metadata and license"
[4]: https://virustotal.github.io/yara-x/docs/intro/installation/ "YARA-X installation documentation"
[5]: https://github.com/bbqsrc/cargo-ndk "cargo-ndk Android build documentation"
[6]: https://github.com/reversinglabs/reversinglabs-yara-rules "ReversingLabs YARA Rules repository"
[7]: https://docs.clamav.net/manual/Development/libclamav.html "ClamAV libclamav documentation"
[8]: https://docs.clamav.net/faq/faq-cvd.html "ClamAV signature database update FAQ"
[9]: https://yarahq.github.io/ "YARA Forge rule curation documentation"

## Implemented design

The app now links a pinned YARA-X 1.22.1 Rust library into the Android app for `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`, using Rust 1.95.0, NDK 27.2.12479018, and `cargo-ndk` 4.1.2. Only YARA-X's PE and ELF modules are enabled because those are the only modules imported by this pinned rules snapshot; unused default modules are excluded to constrain native size and build cost.

Android integration deserves extra care: Wasmtime's Cranelift JIT requires executable-memory behavior that can be restricted by mobile OS policy. YARA-X exposes a `pulley` feature that selects its portable interpreter, so this build enables Pulley across targets; it trades some scan speed for avoiding reliance on runtime JIT code generation. YARA-X itself has an open Android NDK integration issue, and a third-party Android JNI project demonstrates that Android-specific integration work exists. These references make an emulator-backed JNI test important; a successful host compile is not enough. [10] [11] [12]

Before publication, the FLAC pipeline validates container metadata, checks EICAR, then runs the native scanner against a 45-second timeout. Matching signatures block the download; scanner errors or unavailable native libraries also block publication. Cancellation is checked before and after native scanning; the synchronous YARA-X file scan itself cannot be interrupted mid-call, so cancellation during this phase can take until the scan returns or times out. The scan is local and does not send audio or telemetry to a third party.

Rules are embedded at build time from the pinned ReversingLabs MIT snapshot described in `native/yara_x/rules/SOURCE.txt`, with its SHA-256 and license retained in source and APK assets. There is no automatic signature download or auto-update endpoint; the corpus changes through reviewed app source/release updates. Passing is deliberately described as a limited scan, never a malware-free guarantee. A broad file-matching ruleset can produce false positives, so blocked items need an understandable explanation and a user can still use another trusted source or inspect the file independently.

Validation includes Rust unit tests that compile the entire pinned rules corpus and exercise both a synthetic matching rule and a synthetic FLAC negative match, Kotlin unit tests for the download gate's match/error/cancel behavior, and an Android instrumentation test that loads the packaged JNI library and scans a synthetic FLAC offline. GitHub Actions installs the pinned Rust/NDK toolchain, runs host tests, builds the APK, compiles the instrumentation test APK, and runs the JNI smoke test on an x86_64 Android emulator.

[10]: https://docs.wasmtime.dev/stability-platform-support.html "Wasmtime platform support and Pulley portability"
[11]: https://github.com/VirusTotal/yara-x/issues/344 "YARA-X Android NDK integration issue"
[12]: https://github.com/RevEngiSquad/yarax_android "Community Android JNI implementation of YARA-X"
