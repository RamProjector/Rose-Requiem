use jni::objects::{JObject, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::path::Path;
use std::sync::OnceLock;
use std::time::Duration;
use yara_x::{Compiler, Rules, Scanner};

const RULES_SOURCE: &str = include_str!("../rules/reversinglabs.yar");
const SCAN_TIMEOUT: Duration = Duration::from_secs(45);

static COMPILED_RULES: OnceLock<Result<Rules, String>> = OnceLock::new();

fn compile_rules() -> Result<Rules, String> {
    let mut compiler = Compiler::new();
    compiler
        .add_source(RULES_SOURCE)
        .map_err(|error| format!("Bundled YARA rules failed to compile: {error}"))?;
    Ok(compiler.build())
}

fn scan_path(path: &Path) -> Result<Vec<String>, String> {
    let rules = COMPILED_RULES
        .get_or_init(compile_rules)
        .as_ref()
        .map_err(Clone::clone)?;

    let mut scanner = Scanner::new(rules);
    scanner.set_timeout(SCAN_TIMEOUT).use_mmap(true);
    let results = scanner
        .scan_file(path)
        .map_err(|error| format!("YARA-X scan failed: {error}"))?;

    let mut matches = results
        .matching_rules()
        .map(|rule| format!("{}::{}", rule.namespace(), rule.identifier()))
        .collect::<Vec<_>>();
    matches.sort_unstable();
    Ok(matches)
}

fn encode_scan_result(path: &Path) -> String {
    match scan_path(path) {
        Ok(matches) if matches.is_empty() => "CLEAN".to_owned(),
        Ok(matches) => format!("MATCH\n{}", matches.join("\n")),
        Err(error) => format!("ERROR\n{error}"),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_roserequiem_app_data_YaraXNativeScanner_scanNative(
    mut env: JNIEnv,
    _receiver: JObject,
    path: JString,
) -> jstring {
    let path: String = match env.get_string(&path) {
        Ok(value) => value.into(),
        Err(error) => {
            return env
                .new_string(format!("ERROR\nInvalid audio path: {error}"))
                .map_or(std::ptr::null_mut(), |value| value.into_raw());
        }
    };

    let response = catch_unwind(AssertUnwindSafe(|| encode_scan_result(Path::new(&path))))
        .unwrap_or_else(|_| "ERROR\nYARA-X scanner stopped unexpectedly.".to_owned());

    env.new_string(response)
        .map_or(std::ptr::null_mut(), |value| value.into_raw())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bundled_reversinglabs_rules_compile() {
        let rules = compile_rules().expect("pinned ReversingLabs corpus must compile");
        assert!(rules.iter().count() >= 1_000);
    }

    #[test]
    fn bundled_rules_do_not_flag_synthetic_flac() {
        let rules = compile_rules().expect("pinned ReversingLabs corpus must compile");
        let mut fixture = vec![0_u8; 44];
        fixture[0..4].copy_from_slice(b"fLaC");
        fixture[4] = 0x80;
        fixture[7] = 34;
        fixture[42] = 0xff;
        fixture[43] = 0xf8;

        let mut scanner = Scanner::new(&rules);
        scanner.set_timeout(SCAN_TIMEOUT);
        let has_match = scanner
            .scan(&fixture)
            .expect("synthetic FLAC scan succeeds")
            .matching_rules()
            .next()
            .is_some();
        assert!(!has_match, "synthetic audio must not trigger malware rules");
    }

    #[test]
    fn engine_finds_a_matching_rule_and_ignores_nonmatching_input() {
        let mut compiler = Compiler::new();
        compiler
            .add_source(
                r#"
                rule rose_test_marker {
                    strings:
                        $marker = "ROSE_REQUIEM_YARA_TEST_MARKER_8a24"
                    condition:
                        $marker
                }
                "#,
            )
            .expect("test rule compiles");
        let rules = compiler.build();
        let mut scanner = Scanner::new(&rules);
        let found = scanner
            .scan(b"ordinary audio bytes ROSE_REQUIEM_YARA_TEST_MARKER_8a24")
            .expect("test scan succeeds")
            .matching_rules()
            .map(|rule| rule.identifier().to_owned())
            .collect::<Vec<_>>();
        assert_eq!(found, ["rose_test_marker"]);

        let absent = scanner
            .scan(b"ordinary audio bytes only")
            .expect("second scan succeeds")
            .matching_rules()
            .next()
            .is_none();
        assert!(absent);
    }
}
