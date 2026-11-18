import json
import os
from datetime import datetime, timezone
from pathlib import Path
from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile
from pydantic import BaseModel
from sqlalchemy.orm import Session
from .db import session
from .logic import actor_for, clean_summary, next_status, quote_consistent
from .models import Audit, CareRequest, Listing, Message, Quote, Rate, SharedFile, User
from .security import hash_password, issue_token, new_id, read_token, verify_password

api = APIRouter()
MAX_BYTES = 8 * 1024 * 1024
ALLOWED = {".pdf": b"%PDF", ".png": b"\x89PNG", ".jpg": b"\xff\xd8", ".jpeg": b"\xff\xd8"}


def now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def db_session():
    yield from session()


def file_root() -> Path:
    path = Path(os.environ.get("FILE_ROOT", Path(__file__).resolve().parents[1] / "data" / "files"))
    path.mkdir(parents=True, exist_ok=True)
    return path


def audit(db: Session, actor: str, action: str, target: str, detail: str = ""):
    db.add(Audit(id=new_id(), actor_id=actor, action=action, target=target, detail=detail[:500], created_at=now()))


def user_from(request: Request, db: Session) -> User:
    header = request.headers.get("authorization", "")
    token = header[7:] if header.lower().startswith("bearer ") else request.cookies.get("nava_session", "")
    data = read_token(token) if token else None
    user = db.get(User, data["uid"]) if data else None
    if user is None or user.role != data["role"]:
        raise HTTPException(401, "Sign in required")
    return user


def require(user: User, *roles: str):
    if user.role not in roles:
        raise HTTPException(403, "This action is not available for your role")


def visible(user: User, row: CareRequest) -> bool:
    if user.role == "PATIENT":
        return row.patient_id == user.id
    if user.role == "PLATFORM_ADMIN":
        return True
    return user.hospital_id == row.hospital_id and user.role in {"COORDINATOR", "HOSPITAL_ADMIN", "DOCTOR"}


def request_payload(db: Session, row: CareRequest) -> dict:
    quotes = [json.loads(item.payload) for item in db.query(Quote).filter(Quote.request_id == row.id)]
    messages = [
        {"id": item.id, "requestId": item.request_id, "actor": item.actor, "sender": item.sender, "body": item.body, "createdAt": item.created_at}
        for item in db.query(Message).filter(Message.request_id == row.id).order_by(Message.created_at)
    ]
    return {
        "id": row.id, "pathway": row.pathway, "hospitalId": row.hospital_id, "doctorId": row.doctor_id,
        "note": row.note, "documentIds": json.loads(row.document_ids or "[]"), "includeSummary": row.summary_json not in ("", "{}"),
        "authorized": True, "status": row.status, "createdAt": row.created_at, "updatedAt": row.updated_at,
        "quotations": quotes, "messages": messages,
    }


class AuthIn(BaseModel):
    email: str
    password: str
    displayName: str = ""


class SubmitIn(BaseModel):
    id: str
    pathway: str
    hospitalId: str
    doctorId: str = ""
    note: str = ""
    documentIds: list[str] = []
    includeSummary: bool = False
    authorized: bool = False
    summary: dict | None = None


class TransitionIn(BaseModel):
    action: str


class MessageIn(BaseModel):
    body: str


class ListingIn(BaseModel):
    payload: dict


class UserIn(BaseModel):
    email: str
    password: str
    role: str
    hospitalId: str = ""
    displayName: str = ""


class RateIn(BaseModel):
    base: str = "USD"
    perUsd: dict
    source: str
    asOf: str


@api.get("/api/health")
def health():
    return {"ok": True, "service": "nava-shared"}


@api.post("/api/auth/register")
def register(body: AuthIn, request: Request, db: Session = Depends(db_session)):
    email = body.email.strip().lower()
    if len(body.password) < 10 or "@" not in email:
        raise HTTPException(400, "Use a valid email and a password of at least 10 characters")
    if db.query(User).filter(User.email == email).first():
        raise HTTPException(409, "An account with that email already exists")
    user = User(id=new_id(), email=email, password_hash=hash_password(body.password), role="PATIENT", hospital_id="", display_name=body.displayName.strip()[:80] or email)
    db.add(user)
    audit(db, user.id, "register", user.id)
    return {"token": issue_token(user.id, user.role), "role": user.role, "displayName": user.display_name}


@api.post("/api/auth/login")
def login(body: AuthIn, db: Session = Depends(db_session)):
    user = db.query(User).filter(User.email == body.email.strip().lower()).first()
    if user is None or not verify_password(body.password, user.password_hash):
        raise HTTPException(401, "Email or password was not recognized")
    audit(db, user.id, "login", user.id)
    return {"token": issue_token(user.id, user.role), "role": user.role, "displayName": user.display_name, "hospitalId": user.hospital_id}


def listings(kind: str, db: Session):
    return [json.loads(row.payload) for row in db.query(Listing).filter(Listing.kind == kind)]


@api.get("/api/hospitals")
def hospitals(request: Request, db: Session = Depends(db_session)):
    user_from(request, db)
    return listings("hospital", db)


@api.get("/api/doctors")
def doctors(request: Request, db: Session = Depends(db_session)):
    user_from(request, db)
    return listings("clinician", db)


@api.get("/api/stays")
def stays(request: Request, db: Session = Depends(db_session)):
    user_from(request, db)
    return listings("stay", db)


@api.get("/api/rates")
def rates(request: Request, db: Session = Depends(db_session)):
    user_from(request, db)
    row = db.get(Rate, "current")
    return json.loads(row.payload) if row else {"base": "USD", "perUsd": {}, "source": "", "asOf": ""}


@api.post("/api/requests")
def submit(body: SubmitIn, request: Request, db: Session = Depends(db_session)):
    user = user_from(request, db)
    require(user, "PATIENT")
    if not body.authorized or not body.hospitalId:
        raise HTTPException(400, "Explicit authorization and a hospital are required before anything is shared")
    if body.pathway not in {"IVF", "IUI"}:
        raise HTTPException(400, "Pathway must be IVF or IUI")
    if db.get(Listing, body.hospitalId) is None:
        raise HTTPException(404, "Hospital listing was not found")
    summary = clean_summary(body.summary) if body.includeSummary else {}
    row = db.get(CareRequest, body.id)
    if row is None:
        row = CareRequest(id=body.id, patient_id=user.id, hospital_id=body.hospitalId, doctor_id=body.doctorId, pathway=body.pathway,
                          note=body.note[:4000], status="DRAFT", summary_json="{}", document_ids="[]", created_at=now(), updated_at=now())
        db.add(row)
    if row.patient_id != user.id:
        raise HTTPException(403, "This request belongs to another patient")
    if row.status == "DRAFT":
        row.status = next_status("DRAFT", "SUBMIT", "PATIENT")
    elif row.status == "MORE_INFORMATION_REQUIRED":
        row.status = next_status(row.status, "SUBMIT", "PATIENT")
    elif row.status != "SUBMITTED":
        raise HTTPException(409, "This inquiry can no longer be edited by resubmitting it")
    row.hospital_id = body.hospitalId
    row.doctor_id = body.doctorId
    row.note = body.note[:4000]
    row.summary_json = json.dumps(summary)
    row.document_ids = json.dumps(body.documentIds)
    row.updated_at = now()
    db.add(Message(id=new_id(), request_id=row.id, actor="SYSTEM", sender="Nava", body="The patient submitted a treatment inquiry. Only the authorized summary and files are available.", created_at=now()))
    audit(db, user.id, "submit_request", row.id, body.hospitalId)
    db.flush()
    return request_payload(db, row)


@api.get("/api/requests")
def my_requests(request: Request, db: Session = Depends(db_session)):
    user = user_from(request, db)
    rows = db.query(CareRequest).all()
    return [request_payload(db, row) for row in rows if visible(user, row)]


@api.get("/api/requests/{request_id}")
def one_request(request_id: str, request: Request, db: Session = Depends(db_session)):
    user = user_from(request, db)
    row = db.get(CareRequest, request_id)
    if row is None or not visible(user, row):
        raise HTTPException(404, "Request not found")
    payload = request_payload(db, row)
    if user.role != "PATIENT":
        payload["sharedSummary"] = json.loads(row.summary_json or "{}")
        payload["files"] = [{"id": item.id, "name": item.name, "mime": item.mime} for item in db.query(SharedFile).filter(SharedFile.request_id == row.id)]
    return payload


@api.post("/api/requests/{request_id}/transition")
def transition(request_id: str, body: TransitionIn, request: Request, db: Session = Depends(db_session)):
    user = user_from(request, db)
    row = db.get(CareRequest, request_id)
    if row is None or not visible(user, row):
        raise HTTPException(404, "Request not found")
    if body.action == "ISSUE_QUOTE":
        raise HTTPException(400, "A quotation is issued from the quotation endpoint, not by status alone")
    try:
        row.status = next_status(row.status, body.action, user.role)
    except ValueError as exc:
        raise HTTPException(403, str(exc)) from exc
    row.updated_at = now()
    db.add(Message(id=new_id(), request_id=row.id, actor="SYSTEM", sender="Nava", body=f"Status is now {row.status}.", created_at=now()))
    audit(db, user.id, "transition", row.id, body.action)
    return request_payload(db, row)


@api.post("/api/requests/{request_id}/quotes")
def quote(request_id: str, body: dict, request: Request, db: Session = Depends(db_session)):
    user = user_from(request, db)
    require(user, "COORDINATOR", "HOSPITAL_ADMIN", "PLATFORM_ADMIN")
    row = db.get(CareRequest, request_id)
    if row is None or not visible(user, row):
        raise HTTPException(404, "Request not found")
    payload = dict(body)
    payload["id"] = payload.get("id") or new_id()
    payload["requestId"] = row.id
    payload["hospitalId"] = row.hospital_id
    payload.setdefault("demonstration", True)
    payload.setdefault("issuedAt", now())
    if not quote_consistent(payload):
        raise HTTPException(400, "The quotation total must equal the sum of included lines")
    try:
        row.status = next_status(row.status, "ISSUE_QUOTE", user.role)
    except ValueError as exc:
        raise HTTPException(409, str(exc)) from exc
    row.updated_at = now()
    db.add(Quote(id=payload["id"], request_id=row.id, payload=json.dumps(payload)))
    db.add(Message(id=new_id(), request_id=row.id, actor="SYSTEM", sender="Nava", body="A treatment estimate was added by the provider team. It is not a confirmed final bill.", created_at=now()))
    audit(db, user.id, "issue_quote", row.id, payload["id"])
    return request_payload(db, row)


@api.post("/api/requests/{request_id}/messages")
def message(request_id: str, body: MessageIn, request: Request, db: Session = Depends(db_session)):
    user = user_from(request, db)
    row = db.get(CareRequest, request_id)
    if row is None or not visible(user, row):
        raise HTTPException(404, "Request not found")
    text = body.body.strip()
    if not text or len(text) > 4000:
        raise HTTPException(400, "Write a message under 4000 characters")
    db.add(Message(id=new_id(), request_id=row.id, actor=actor_for(user.role), sender=user.display_name or user.role, body=text, created_at=now()))
    row.updated_at = now()
    audit(db, user.id, "message", row.id)
    return request_payload(db, row)


@api.post("/api/requests/{request_id}/documents")
async def upload(request_id: str, request: Request, file: UploadFile = File(...), document_id: str = Form(""), db: Session = Depends(db_session)):
    user = user_from(request, db)
    require(user, "PATIENT")
    row = db.get(CareRequest, request_id)
    if row is None or row.patient_id != user.id:
        raise HTTPException(404, "Request not found")
    allowed_ids = set(json.loads(row.document_ids or "[]"))
    if not document_id or document_id not in allowed_ids:
        raise HTTPException(403, "That file was not included in the sharing authorization")
    raw = await file.read(MAX_BYTES + 1)
    suffix = Path(file.filename or "").suffix.lower()
    if suffix not in ALLOWED or len(raw) == 0 or len(raw) > MAX_BYTES or not raw.startswith(ALLOWED[suffix]):
        raise HTTPException(400, "Upload a PDF, JPG or PNG under 8 MB")
    stored = file_root() / new_id()
    stored.write_bytes(raw)
    item = SharedFile(id=new_id(), request_id=row.id, name=(file.filename or "file")[:80], mime=file.content_type or "application/octet-stream", path=str(stored), size=len(raw))
    db.add(item)
    audit(db, user.id, "upload", row.id, item.name)
    return {"id": item.id, "name": item.name}


@api.get("/api/files/{file_id}")
def download(file_id: str, request: Request, db: Session = Depends(db_session)):
    from fastapi.responses import FileResponse
    user = user_from(request, db)
    item = db.get(SharedFile, file_id)
    row = db.get(CareRequest, item.request_id) if item else None
    if item is None or row is None or not visible(user, row):
        raise HTTPException(404, "File not found")
    audit(db, user.id, "download", item.id)
    return FileResponse(item.path, filename=item.name, media_type=item.mime)


@api.post("/api/admin/listings/{kind}")
def add_listing(kind: str, body: ListingIn, request: Request, db: Session = Depends(db_session)):
    user = user_from(request, db)
    require(user, "PLATFORM_ADMIN", "HOSPITAL_ADMIN")
    if kind not in {"hospital", "clinician", "stay"}:
        raise HTTPException(404, "Unknown listing type")
    payload = body.payload
    payload["id"] = str(payload.get("id") or new_id())
    payload["demonstration"] = bool(payload.get("demonstration", True))
    if user.role == "HOSPITAL_ADMIN":
        owner = payload.get("id") if kind == "hospital" else payload.get("hospitalId")
        if owner != user.hospital_id:
            raise HTTPException(403, "You can edit listings only for your hospital")
    db.merge(Listing(id=payload["id"], kind=kind, payload=json.dumps(payload)))
    audit(db, user.id, "listing", payload["id"], kind)
    return payload


@api.post("/api/admin/users")
def add_user(body: UserIn, request: Request, db: Session = Depends(db_session)):
    user = user_from(request, db)
    require(user, "PLATFORM_ADMIN")
    if body.role not in {"PLATFORM_ADMIN", "HOSPITAL_ADMIN", "DOCTOR", "COORDINATOR", "PATIENT"}:
        raise HTTPException(400, "Unknown role")
    if len(body.password) < 10:
        raise HTTPException(400, "Password must be at least 10 characters")
    email = body.email.strip().lower()
    if db.query(User).filter(User.email == email).first():
        raise HTTPException(409, "An account with that email already exists")
    created = User(id=new_id(), email=email, password_hash=hash_password(body.password), role=body.role, hospital_id=body.hospitalId, display_name=body.displayName[:80])
    db.add(created)
    audit(db, user.id, "create_user", created.id, body.role)
    return {"id": created.id, "email": created.email, "role": created.role}


@api.post("/api/admin/rates")
def set_rates(body: RateIn, request: Request, db: Session = Depends(db_session)):
    user = user_from(request, db)
    require(user, "PLATFORM_ADMIN")
    if not body.source.strip():
        raise HTTPException(400, "Name the source of these rates")
    payload = {"base": body.base, "perUsd": body.perUsd, "source": body.source.strip(), "asOf": body.asOf}
    db.merge(Rate(id="current", payload=json.dumps(payload)))
    audit(db, user.id, "rates", "current", body.source[:120])
    return payload
