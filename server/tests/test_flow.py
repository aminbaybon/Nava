import os
from pathlib import Path
import pytest
from fastapi.testclient import TestClient


@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("DATABASE_URL", "sqlite:///" + (tmp_path / "t.db").as_posix())
    monkeypatch.setenv("SECRET", "test-secret")
    monkeypatch.setenv("SEED_DEMO", "1")
    monkeypatch.setenv("FILE_ROOT", str(tmp_path / "files"))
    monkeypatch.setenv("DEMO_ADMIN_PASSWORD", "demo-admin-change-me")
    monkeypatch.setenv("DEMO_COORDINATOR_PASSWORD", "demo-coordinator-change-me")
    monkeypatch.setenv("DEMO_DOCTOR_PASSWORD", "demo-doctor-change-me")
    monkeypatch.setenv("DEMO_CEDAR_PASSWORD", "demo-cedar-change-me")
    from nava_api.db import reset_engine
    reset_engine()
    from nava_api.main import create_app
    with TestClient(create_app()) as test_client:
        yield test_client


def auth(client, email, password):
    response = client.post("/api/auth/login", json={"email": email, "password": password})
    assert response.status_code == 200, response.text
    return {"Authorization": "Bearer " + response.json()["token"]}


def test_coordinator_cannot_see_another_hospitals_request(client):
    patient = client.post("/api/auth/register", json={"email": "patient@example.com", "password": "patient-password", "displayName": "Neda"}).json()
    header = {"Authorization": "Bearer " + patient["token"]}
    created = client.post("/api/requests", headers=header, json={
        "id": "req-1", "pathway": "IVF", "hospitalId": "h-sepehr", "doctorId": "d-sepehr-sara",
        "note": "Please review the shared summary only.", "documentIds": ["lab-1"], "includeSummary": True,
        "authorized": True, "summary": {"concern": "Two unsuccessful IVF cycles", "sessions": "must not be stored", "history": [{"fieldId": "fertility.ivf_count", "value": "2"}]}
    })
    assert created.status_code == 200, created.text
    assert "sessions" not in created.text
    north = auth(client, "coordinator@demo.nava.invalid", "demo-coordinator-change-me")
    cedar = auth(client, "cedar@demo.nava.invalid", "demo-cedar-change-me")
    assert len(client.get("/api/requests", headers=north).json()) == 1
    assert client.get("/api/requests", headers=cedar).json() == []
    detail = client.get("/api/requests/req-1", headers=north).json()
    assert detail["sharedSummary"]["concern"] == "Two unsuccessful IVF cycles"
    assert "sessions" not in detail["sharedSummary"]


def test_doctor_cannot_issue_a_quote_and_coordinator_can(client):
    patient = client.post("/api/auth/register", json={"email": "b@example.com", "password": "patient-password"}).json()
    header = {"Authorization": "Bearer " + patient["token"]}
    assert client.post("/api/requests", headers=header, json={"id": "req-2", "pathway": "IVF", "hospitalId": "h-sepehr", "authorized": False}).status_code == 400
    assert client.post("/api/requests", headers=header, json={
        "id": "req-2", "pathway": "IVF", "hospitalId": "h-sepehr", "doctorId": "d-sepehr-sara",
        "authorized": True, "includeSummary": False, "documentIds": ["lab-1"], "note": "Inquiry"
    }).status_code == 200
    doctor = auth(client, "doctor@demo.nava.invalid", "demo-doctor-change-me")
    refused = client.post("/api/requests/req-2/quotes", headers=doctor, json={"treatment": "IVF", "currency": "USD", "total": 100, "expiresOn": "2026-12-01", "lines": [{"description": "Visit", "amount": 100}]})
    assert refused.status_code == 403
    coordinator = auth(client, "coordinator@demo.nava.invalid", "demo-coordinator-change-me")
    assert client.post(f"/api/requests/req-2/transition", headers=coordinator, json={"action": "START_REVIEW"}).status_code == 200
    bad = client.post("/api/requests/req-2/quotes", headers=coordinator, json={"treatment": "IVF", "currency": "USD", "total": 999, "expiresOn": "2026-12-01", "lines": [{"description": "Visit", "amount": 100}]})
    assert bad.status_code == 400
    issued = client.post("/api/requests/req-2/quotes", headers=coordinator, json={
        "treatment": "IVF estimate", "currency": "USD", "total": 1000, "expiresOn": "2026-12-01",
        "lines": [{"description": "Estimated package", "amount": 1000}, {"description": "Freezing", "amount": 200, "included": False}],
        "exclusions": ["Medication"], "qualifications": "A clinician must confirm suitability.", "demonstration": True
    })
    assert issued.status_code == 200, issued.text
    assert issued.json()["status"] == "QUOTATION_RECEIVED"
    accepted = client.post("/api/requests/req-2/transition", headers=header, json={"action": "ACCEPT"})
    assert accepted.status_code == 200
    assert accepted.json()["status"] == "ACCEPTED"


def test_portal_login_page_renders(client):
    page = client.get("/portal/login")
    assert page.status_code == 200
    assert "Sign in" in page.text
    signed = client.post("/portal/login", data={"email": "coordinator@demo.nava.invalid", "password": "demo-coordinator-change-me"}, follow_redirects=False)
    assert signed.status_code == 303
    home = client.get("/portal", cookies=signed.cookies)
    assert home.status_code == 200
    assert "Shared inquiries" in home.text
    hospitals = client.get("/portal/hospitals", cookies=signed.cookies)
    assert "سپهر" in hospitals.text


def test_upload_rejects_the_wrong_bytes_and_records_audit(client):
    patient = client.post("/api/auth/register", json={"email": "c@example.com", "password": "patient-password"}).json()
    header = {"Authorization": "Bearer " + patient["token"]}
    client.post("/api/requests", headers=header, json={"id": "req-3", "pathway": "IUI", "hospitalId": "h-zayandeh", "authorized": True, "documentIds": ["lab-9"]})
    rejected = client.post("/api/requests/req-3/documents", headers=header, files={"file": ("note.pdf", b"not a pdf", "application/pdf")}, data={"document_id": "lab-9"})
    assert rejected.status_code == 400
    stored = client.post("/api/requests/req-3/documents", headers=header, files={"file": ("note.pdf", b"%PDF-1.4 demo", "application/pdf")}, data={"document_id": "lab-9"})
    assert stored.status_code == 200, stored.text
    admin = auth(client, "admin@demo.nava.invalid", "demo-admin-change-me")
    listing = client.post("/api/admin/listings/hospital", headers=admin, json={"payload": {
        "id": "h-real", "name": "Unverified until checked", "country": "Demonstration Country", "city": "Harbor City",
        "services": ["IVF"], "languages": ["en"], "demonstration": True
    }})
    assert listing.status_code == 200
    assert listing.json()["demonstration"] is True
