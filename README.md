# Nava — fertility intake for Android

**Native Kotlin / Compose development build by Amin Fathi.** A local-first IVF/IUI history-taking application with BYOK OpenAI inference, encrypted records and deterministic reports. It is an implementation for engineering and clinical review, **not a medically validated or certified product**.

**Source-only delivery:** 27 JVM tests passed. The full Compose build was blocked by an unavailable compiler dependency; no APK is included. See the exact build blocker in [validation](docs/VALIDATION.md).

The same app now includes a medical-tourism journey: documents, hospital and doctor discovery, treatment requests, estimates, accommodation and a travel checklist. Shared inquiries go through the care service in `server/`. Demonstration listings are fictional. Details, local accounts and privacy limits are in [docs/TOURISM.md](docs/TOURISM.md).

## راه‌اندازی سریع

1. پروژه را در Android Studio باز کنید و طبق بخش Build بسازید. در این بسته APK وجود ندارد؛ دریافت کامپایلر Compose در محیط ساخت مسدود شد. خروجی ساخته‌شده روی اندروید ۸ یا بالاتر نصب می‌شود.
2. زبان را انتخاب کنید و یک پروفایل با نام دلخواه بسازید.
3. از Settings کلید OpenAI خود را ذخیره کنید، مدل‌ها را دریافت و مدل انتخابی را آزمایش کنید. آزمون مدل با متن ساختگی است و ممکن است هزینه API داشته باشد.
4. مسیر IVF یا IUI و مرحلهٔ درمان را مشخص کنید. رضایت ارسال داده برای هر بیمار جداگانه گرفته می‌شود.
5. گفت‌وگو را شروع کنید. در صورت خطا پاسخ حفظ می‌شود؛ Retry همان پاسخ را دوباره پردازش می‌کند.
6. در Medical record اطلاعات استخراج‌شده را تأیید یا اصلاح کنید. موارد متناقض نیاز به اصلاح صریح دارند.
7. گزارش را بسازید، PDF یا JSON را در مقصد دلخواه ذخیره کنید و در صورت نیاز با دکمهٔ اشتراک بفرستید.

این نسخه برای تست با دادهٔ ساختگی تهیه شده است. ترجمه‌های پزشکی، رفتار مدل، قفل دستگاه و خروجی PDF باید روی دستگاه واقعی و با نظر متخصص بررسی شوند. کلید API را برای توسعه‌دهنده ارسال نکنید؛ آن را داخل برنامه وارد کنید.

## Implemented

- First-run language selection: English, Persian, Arabic, Armenian, Azerbaijani Latin, Urdu Pakistan, Sorani and Kurmanji; Android resource localization, explicit RTL direction, localized UI numbers, dark/light/system themes.
- Independent local patient profiles; IVF/IUI and stage selection; per-patient consent/revocation; new sessions reusing dated history; previous conversations and deletion.
- Official OpenAI Responses API; model discovery; synthetic JSON-schema and streaming capability tests; selectable model; encrypted BYOK; cancellable streaming; bounded retry for HTTP 429/5xx; sanitized error categories.
- Native deterministic orchestration, typed extraction, evidence provenance, explicit conflicts, patient review, versioned coverage and repeated IVF/IUI/pregnancy branches.
- SQLCipher database through AndroidX SQLite, random database key wrapped with Android Keystore AES-GCM, sealed API key, no analytics or network logging, backup exclusions, secure-window flag and optional biometric/device-credential UI lock.
- Offline record review, local corrections and immutable report snapshots; Unicode PDF and JSON export through Android Storage Access Framework; explicit Android sharing with URI permission.
- Domain tests, mocked API tests, instrumented encryption/PDF tests, synthetic model evaluation fixtures and a resource/security source check. See [validation](docs/VALIDATION.md) for **what actually ran**.

## Build

Install JDK 17 or compatible full JDK, Android SDK Platform 36 and Build Tools 35.0.0. Open the root project in Android Studio, set its Gradle JDK to 17+, accept SDK licenses, and let dependencies sync. `local.properties` is deliberately not distributed; Android Studio creates your own `sdk.dir`.

```bash
./gradlew :domain:test :verification:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python3 scripts/check_resources.py
./gradlew :domain:exportSpecification
# A connected emulator/device is required for the following:
./gradlew :app:connectedDebugAndroidTest
```

Windows PowerShell: use `./gradlew.bat` for the same tasks. Output: `app/build/outputs/apk/debug/app-debug.apk`. Minimum Android API 26; target and compile API 36. No server, Python runtime, vector database or web wrapper is shipped in the app.

The pinned dependency baseline uses AGP 8.11.1, Gradle 8.13, Kotlin 2.1.20, Compose BOM 2025.05.01, AndroidX SQLite 2.5.1, SQLCipher Android 4.6.1, OkHttp 4.12.0 and DataStore 1.1.7. These are explicit compatible development pins, not a claim that every dependency is the latest available. Audit/update dependencies before release.

## Release signing

`assembleRelease` creates an unsigned release unless the following variables are supplied securely:

```
NAVA_KEYSTORE=<absolute path to your private keystore>
NAVA_STORE_PASSWORD=<secret>
NAVA_KEY_ALIAS=<alias>
NAVA_KEY_PASSWORD=<secret>
```

Run `./gradlew :app:assembleRelease` or `:app:bundleRelease`. Do not commit signing material, API keys, local.properties or real patient fixtures. The debug signing identity is not a production release identity. Release signing alone does not establish clinical readiness.

## Review documents

- [Architecture and state invariants](docs/ARCHITECTURE.md)
- [Clinical provenance and coverage](docs/CLINICAL.md)
- [Machine-readable field specification](docs/clinical-specification.json)
- [Threat model and privacy limitations](docs/SECURITY.md)
- [Validation and release blockers](docs/VALIDATION.md)
- [Acceptance walkthrough](docs/ACCEPTANCE.md)
- [Synthetic model evaluation](evaluation/README.md)

## Important boundaries

A model call transmits selected patient facts and recent messages to OpenAI. `store:false` disables response storage for this request pattern; it does **not** establish zero provider retention. Users need their own provider account, compatible model, connectivity and credits.

The record is patient-reported. Extracted values remain marked unreviewed until confirmed. Exact-quote provenance prevents unsupported source attribution but is not proof of correct medical extraction. The coverage percentage means required applicable topics were addressed, including unknown/skipped responses; it does not mean all medical information is known or that clinical evaluation is complete.

The app never intentionally selects treatment or prescribes. Safety keyword checks are a conservative secondary screen with known false positives and false negatives. The AI and translations have not been clinically certified. Do not use this development build as an emergency triage service or for unsupervised clinical deployment.
