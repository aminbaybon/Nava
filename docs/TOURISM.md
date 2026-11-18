# Medical tourism extension

Nava remains a local-first Android intake app. This extension adds the pre-travel journey around that interview: a travel profile, document review, a provider directory, treatment inquiries, estimates, accommodation and a trip checklist.

It is an engineering MVP. It is not a certified medical device, not a visa service, and not evidence of a hospital partnership.

## What stays on the phone

The encrypted patient aggregate still holds the interview, consent, facts, reports and files. Travel preferences, extracted document lines, drafts, notices and the concierge log were added as fields with defaults, so a record written by the previous version still opens. The SQLCipher schema version is unchanged because the SQL table is still one payload per patient.

Document text is read with the existing OpenAI page reader. A second structured call may propose clinical values. A value is kept only when its evidence is a verbatim span of that text. It stays unverified until the patient confirms it. Unconfirmed text is not treated as a medical fact. The summary is assembled from confirmed entries and interview answers. An optional English rendering is labeled as a machine translation.

## What can leave the phone

Nothing is uploaded because a hospital exists in the directory. The patient chooses a hospital, optional doctor, files and whether to include the confirmed summary, then ticks an authorization. Only that package is posted. The interview transcript is not part of the payload, and the service drops unexpected summary fields.

The Android client accepts `https` service addresses. `http` is limited to `localhost`, `127.0.0.1` and `10.0.2.2` so a development server on the computer or emulator can be used. Other cleartext hosts are rejected. Production hosting should be HTTPS.

## Care service

`server/` is a FastAPI application.

- Default database: SQLite at `server/data/nava.db`.
- PostgreSQL: set `DATABASE_URL` to a SQLAlchemy URL. Install a PostgreSQL driver in that environment. The tables are created on startup and do not embed a hosted cluster.
- Shared files: `server/data/files`, outside the web root, PDF/JPEG/PNG only, 8 MB maximum, checked by file signature. Access requires an authenticated user who can already see that inquiry.
- Passwords: PBKDF2-HMAC-SHA256. Sessions: signed tokens, 12 hours.
- Roles: `PLATFORM_ADMIN`, `HOSPITAL_ADMIN`, `COORDINATOR`, `DOCTOR`, `PATIENT`. A coordinator or doctor sees inquiries only for their hospital. A doctor can read and message; issuing an estimate is limited to coordinator and administrator roles. Platform administrators can manage listings and rates and can see shared inquiries for operations. They still do not receive the on-device interview.
- Estimates must total the included lines. Optional lines are excluded from the total. Status changes follow the same table as the Android domain rules.
- Audit rows record sign-in, submission, status changes, estimates, uploads, downloads, listing edits and rate changes. They do not store the password.

Run from the repository root, with the virtual environment created from `server/requirements.txt`:

```text
server\.venv\Scripts\python.exe -m uvicorn nava_api.main:app --app-dir server --host 127.0.0.1 --port 8000
```

The provider portal is `http://127.0.0.1:8000/portal`. API docs are `/api/docs`.

Set `SEED_DEMO=0` before any deployment that should not contain fictional listings. Set `SECRET` to a long random value. Replace the demonstration passwords. Set `COOKIE_SECURE` behavior by serving HTTPS; the local cookie is not marked Secure because the development server is HTTP.

## Demonstration data

`app/src/main/assets/demo-catalog.json` is the seed. Cities and neighborhoods are real (Tehran Vanak, Isfahan Chaharbagh, Shiraz Maaliabad, Mashhad Ahmadabad, Tabriz Valiasr, Yazd Safayieh). Clinic names and clinician names are Persian proper nouns used as planning samples; they are not a verified partnership or a real physician's license. Names stay in Persian and are not translated. Each stay lists rooms, guests, and a suggested number of nights. Accreditation is empty. Prices are labeled as planning ranges, not quotations. An estimate appears only after a coordinator or administrator enters one in the portal. The app will not invent one while offline.

An administrator can replace a listing without a code change. Clearing the demonstration flag is an administrative assertion that the text was verified. The software does not check that assertion against a registry.

Exchange rates ship as an administrator table dated 2026-11-18 and described as a demonstration table. The portal can replace them. Conversions are shown as approximate. There is no live market-rate feed unless an operator later adds one.

Distances are administrator-entered. The in-app map is a schematic of those coordinates, and the external map button opens the device map app. The app does not book rooms, flights or visas.

## Concierge

`TourismOrchestrator` picks one deterministic path: directory lookup, document prompts already stored on a request, estimate comparison, stay distance, or the next request state. It does not make seven model calls. Clinical interviewing remains `InterviewOrchestrator`. The concierge is labeled as the assistant and cannot send a message under a hospital or physician identity.

## Notifications

System alerts, when the patient allows them, use the generic sentence “You have an update in Nava” and secret lock-screen visibility. The in-app list names the event type, not laboratory values.

## Hosting, retention and cross-border transfer

This repository does not select a cloud region or a retention period. Operators should choose a region consciously: a shared summary and file are copied to the care service, which may be in a different country from the patient. Keep the service and database encrypted at rest using the host’s disk or database encryption, restrict administrator accounts, and define how long inquiries, files and audit rows are kept. Deleting the on-device record does not delete a copy already shared with a hospital.

No claim is made of GDPR, HIPAA, medical-device, or fertility-clinic accreditation compliance. Those require a separate review of the operator, the contracts and the destination.

## Tests

`server/tests/test_flow.py` covers authorization, hospital isolation, estimate totals, file-type rejection and the portal sign-in page. `domain` tests cover evidence checks, filters, transitions and legacy records. The Android Gradle build still needs a JDK and the Android SDK named in `local.properties`.
