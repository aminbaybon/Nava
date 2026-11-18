# Validation record — 18 November 2026

**Delivery status: source implementation for review. No APK was generated. The complete Android/Compose build is not verified.**

## Results actually obtained

| Check | Result |
|---|---|
| Kotlin domain tests | **21 passed, 0 failures, 0 errors** |
| Production API adapter/prompt code compiled in JVM verification module | Passed |
| API adapter tests using an in-memory OkHttp interceptor | **6 passed, 0 failures, 0 errors**; no live API calls |
| Localization/source privacy checks | Passed: **198 resource keys × 8 locales**, manifest exclusions, HTTPS endpoint, store=false, no hardcoded API key or logging interceptor |
| Synthetic evaluation fixtures | Passed structural check: **24 fictional cases**, expected field IDs exist; **not a live AI evaluation** |
| Clinical specification export | Passed: **89 base and repeatable-template field definitions**; runtime expands additional numbered cycles/pregnancies |
| Android resource and manifest processing | Passed during attempted builds |
| Full Debug APK build | **Blocked**: `org.jetbrains.kotlin:kotlin-compose-compiler-plugin-embeddable:2.1.20` unavailable in cache and Maven download blocked by environment network policy |
| Android Lint | Not completed; task graph blocked by the missing Compose compiler dependency |
| Instrumented tests | Authored, not executed; no emulator/device available and full build blocked |
| Real OpenAI connectivity/model conversations | Not run; no API key supplied |
| Native 16 KB check | arm64 SQLCipher ELF LOAD segments inspected: alignment `0x4000`; no complete APK zip alignment/device claim |
| Clinical review, translation review, security certification | Not performed |
| Production release signing | Not performed; no private signing credentials supplied |

The final successful JVM verification command was:

```bash
./gradlew :domain:test :verification:test --offline --no-daemon --max-workers=2
```

`verification` compiles the actual provider and prompt sources from `app`, not rewritten test doubles of their implementation. Its fake interceptor replaces only HTTP transport and lets tests inspect requests and feed SSE/JSON/error responses. The included `test-results.json` records case names and counts.

## Android build investigation

Gradle 8.13, a full JDK 17, SDK Platform 36 and Build Tools 35 were obtained. Room's code-generation dependency could not be downloaded; the final repository uses direct AndroidX SQLite + SQLCipher with explicit transactions and a committed SQL schema instead. The final remaining build blocker is the Compose compiler artifact above. The final project retains Compose configuration and does not disable it to manufacture an unusable APK.

A temporary copy was used for a limited Kotlin front-end check without the unavailable Compose plugin. It found a `PdfDocument.use` type error, which was corrected to explicit try/finally closure. The check then reached the expected Compose backend/inlining failure. This is **not** a successful Android build, Compose semantic check, UI test or runtime validation. No output from that temporary copy is included as a build artifact.

## Reproduce the complete validation on a network-enabled development machine

```bash
./gradlew :domain:test :verification:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
python3 scripts/check_resources.py
python3 evaluation/run.py --check
```

Use JDK 17+, SDK 36/Build Tools 35, and working Google/Maven Central access. Android Studio will create a machine-specific `local.properties`. The source ZIP contains the Gradle wrapper, source, resources, SQL schema, specifications, synthetic fixtures and documentation; it excludes SDKs, dependency caches, signing keys and local configuration.

## Release blockers beyond compilation

- A fertility specialist must approve the grouped clinical narrative fields, applicability rules and escalation wording. Topic coverage is not exhaustive subquestion verification.
- Native speakers must review all clinical translations and script-specific display. Urdu/Sorani/Armenian PDF shaping and pagination need actual-device inspection.
- Live model evaluation must measure extraction entailment, repeated questions, unsupported advice, semantic safety, prompt injection, latency and costs. Exact evidence quotes are necessary but not sufficient.
- The safety phrase screen is conservative and incomplete; its false-positive/false-negative rates are unknown. A known advice request receives a local, translated boundary response, but model classification and unrecognized unsafe output still require validation.
- Streaming output is provisional before final validation. It must not be treated as verified clinical content or medical advice.
- Biometric/device credential behavior, idle/background locking, backups, deletion, report export and restart recovery must be exercised on supported devices. Android 26–29 uses device credential confirmation; Android 30+ can use strong biometrics or device credentials.
- Independently assess mobile security, dependency vulnerabilities, privacy agreements and applicable clinical deployment requirements. No compliance/certification claim is made.
