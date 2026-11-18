# Security and privacy engineering review

This is an engineering threat model using [OWASP MASVS](https://mas.owasp.org/MASVS/) as a review baseline. It is not a completed MASVS assessment or a HIPAA/GDPR/medical-device certification.

| Threat | Implemented control | Residual risk / required verification |
|---|---|---|
| Copied application files | SQLCipher encrypts the complete clinical database; 256-bit random database key is AES-GCM sealed by Android Keystore | A compromised/rooted/unlocked process can access plaintext; hardware-backed key availability depends on device. |
| API key theft from preferences | Keystore AES-GCM, random IV, authenticated name as AAD; masked input; no build-time secret | BYOK cannot guarantee secrecy on a compromised device. Keys exist in memory while requests run. |
| Backup or device migration leakage | allowBackup=false, fullBackupContent=false, cloud/device-transfer exclusions | Test on target OEM devices and supported OS versions; do not assume flags are a full forensic guarantee. |
| Screenshots / recents | FLAG_SECURE on the activity | Does not stop physical cameras or a compromised operating system. |
| Unattended app | Optional BiometricPrompt/device credential gate, background lock and two-minute inactivity lock | UI gate is not cryptographic per-operation auth. Initial record load occurs behind the gate. Test Android 26–36 device behavior. |
| Network interception | Official OpenAI calls use HTTPS, default TLS and system trust anchors. The care service must be HTTPS except loopback (`localhost`, `127.0.0.1`, `10.0.2.2`), which may use HTTP only for a local development server. | No certificate pinning. A local cleartext exception must not be reused for a public host. |
| Prompt injection | Fixed system responsibilities, JSON schema, allowlisted field IDs, source-quote checks, deterministic coverage and no model tools | This does not prove model correctness or fully prevent misleading model output. |
| Wrong patient commit | Captured profile/session/revision, transaction compare-and-set, serialized turns, explicit export/report association | One device owner can access every local profile; there is no multi-user clinical ACL. |
| Accidental background transmission | No background worker; stop cancels active network; consent checked per attempt and commit | Data already transmitted before revoke/cancel cannot be recalled. |
| Plaintext exports | SAF direct stream; explicit export warning; no plaintext internal cache; explicit share with URI read grant | Exported PDF/JSON are not encrypted by Nava. Destination provider or recipient controls later retention. |
| Deletion remnants | SQL delete with secure_delete=ON, encrypted rollback journals; conversation deletion also removes linked facts and reports | No promise of physical-media erasure; filesystem snapshots, exports and provider-side copies are out of scope. |
| Logs / analytics | No logging interceptor, analytics SDK, crash upload or raw provider-error display | Audit OS/device logs and downstream integrations before production. |
| Lost/corrupt Keystore state | Fail closed; do not regenerate a key over an existing database | Recovery/export is unavailable without the key; no cloud recovery service is present. |

## Provider boundary

[OpenAI data controls](https://developers.openai.com/api/docs/guides/your-data) distinguish application storage from provider retention. Requests set `store:false` and do not send a previous-response or conversation ID. The app still sends selected health data, and applicable provider policies and account agreements govern that processing. Do not claim offline inference, zero retention or a health-data processing agreement merely because the API key works.

Consent version `2026-11-18.1` and acceptance time are stored in the patient's encrypted record. Revocation disables future AI requests for that profile while keeping offline review available. Model validation uses only synthetic text. API cost and incompatible models are explicitly disclosed.

## Before any healthcare deployment

Obtain jurisdiction-specific privacy and medical-software classification review; determine the controller/processor roles, applicable agreements, lawful processing basis, age/consent policy and intended clinical use. Validate translations and safety behavior with clinicians and representative patients. Perform independent penetration testing, dependency/SBOM review, native-library 16 KB page compatibility verification, device auth/backup tests, accessibility testing and release-signing/key management. These are release conditions, not claimed completed certifications.

Shared hospital inquiries, quotations and coordinator messages use a separate service. The private interview is not uploaded by default. See [medical tourism architecture](TOURISM.md) for retention, roles and the demonstration-data boundary. That document is not a regulatory determination.

## Dependency and migration posture

Dependencies are pinned. No destructive database migration is allowed. The schema snapshot is included. The current Keystore alias is versioned, but key rotation/recovery UI and cryptographic erasure of only one profile have not been implemented. Future updates need tested database and aggregate migrations rather than just increasing the SQLite schema version.
