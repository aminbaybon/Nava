-- SQLCipher user_version=1. Encrypted database, not an unencrypted export.
CREATE TABLE patients (
    id TEXT NOT NULL PRIMARY KEY,
    payload TEXT NOT NULL,
    revision INTEGER NOT NULL
);
PRAGMA secure_delete=ON;
-- payload schemaVersion=1: Patient aggregate defined in domain/Models.kt.
-- Attachment bytes are not stored in this payload. Metadata, transcription, and description are.
-- Upgrades/downgrades fail closed until a reviewed migration is implemented.
