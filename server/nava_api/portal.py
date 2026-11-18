"""Provider portal. Cookie session, role checks, and the same APIs as the Android client."""
import json
import os
from fastapi import APIRouter, Depends, Form, HTTPException, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from pathlib import Path
from sqlalchemy.orm import Session
from .api import audit, db_session, listings, now, request_payload, user_from
from .logic import quote_consistent
from .models import CareRequest, Listing, Quote, Rate, User
from .security import issue_token, new_id

portal = APIRouter()
templates = Jinja2Templates(directory=str(Path(__file__).resolve().parents[1] / "templates"))


def page(request: Request, db: Session, name: str, **extra):
    try:
        user = user_from(request, db)
    except HTTPException:
        return RedirectResponse("/portal/login", status_code=303)
    banner = "Demonstration environment. Listings and accounts shipped with the repository are fictional."
    context = {"request": request, "user": user, "banner": banner, "csrf": request.cookies.get("nava_csrf", ""), **extra}
    return templates.TemplateResponse(request, name, context)


def csrf_ok(request: Request, token: str) -> bool:
    cookie = request.cookies.get("nava_csrf", "")
    return bool(cookie) and cookie == token


@portal.get("/portal/login", response_class=HTMLResponse)
def login_page(request: Request):
    return templates.TemplateResponse(request, "login.html", {"request": request, "error": ""})


@portal.post("/portal/login")
def login_post(request: Request, email: str = Form(""), password: str = Form(""), db: Session = Depends(db_session)):
    from .api import login as api_login
    from .api import AuthIn
    try:
        result = api_login(AuthIn(email=email, password=password), db)
    except HTTPException:
        return templates.TemplateResponse(request, "login.html", {"request": request, "error": "Email or password was not recognized"}, status_code=401)
    csrf = new_id()
    secure = os.environ.get("COOKIE_SECURE", "0") == "1"
    response = RedirectResponse("/portal", status_code=303)
    response.set_cookie("nava_session", result["token"], httponly=True, samesite="lax", secure=secure, max_age=60 * 60 * 12)
    response.set_cookie("nava_csrf", csrf, httponly=False, samesite="lax", secure=secure, max_age=60 * 60 * 12)
    return response


@portal.post("/portal/logout")
def logout():
    response = RedirectResponse("/portal/login", status_code=303)
    response.delete_cookie("nava_session")
    response.delete_cookie("nava_csrf")
    return response


@portal.get("/portal", response_class=HTMLResponse)
def home(request: Request, db: Session = Depends(db_session)):
    user = None
    try:
        user = user_from(request, db)
    except HTTPException:
        return RedirectResponse("/portal/login", status_code=303)
    rows = [row for row in db.query(CareRequest).all() if user.role == "PLATFORM_ADMIN" or row.hospital_id == user.hospital_id or (user.role == "PATIENT" and row.patient_id == user.id)]
    return page(request, db, "home.html", requests=rows)


@portal.get("/portal/hospitals", response_class=HTMLResponse)
def hospitals(request: Request, db: Session = Depends(db_session)):
    return page(request, db, "listings.html", title="Hospitals", kind="hospital", rows=listings("hospital", db))


@portal.get("/portal/doctors", response_class=HTMLResponse)
def doctors(request: Request, db: Session = Depends(db_session)):
    return page(request, db, "listings.html", title="Doctors", kind="clinician", rows=listings("clinician", db))


@portal.get("/portal/stays", response_class=HTMLResponse)
def stays(request: Request, db: Session = Depends(db_session)):
    return page(request, db, "listings.html", title="Accommodation", kind="stay", rows=listings("stay", db))


@portal.get("/portal/listings/{kind}/new", response_class=HTMLResponse)
def new_listing(kind: str, request: Request, db: Session = Depends(db_session)):
    return page(request, db, "edit.html", kind=kind, payload={"demonstration": True}, error="")


@portal.get("/portal/listings/{kind}/{item_id}", response_class=HTMLResponse)
def edit_listing(kind: str, item_id: str, request: Request, db: Session = Depends(db_session)):
    row = db.get(Listing, item_id)
    if row is None:
        raise HTTPException(404, "Listing not found")
    return page(request, db, "edit.html", kind=kind, payload=json.loads(row.payload), error="")


@portal.post("/portal/listings/{kind}")
def save_listing(kind: str, request: Request, csrf: str = Form(""), payload: str = Form(""), db: Session = Depends(db_session)):
    user = user_from(request, db)
    if user.role not in {"PLATFORM_ADMIN", "HOSPITAL_ADMIN"} or not csrf_ok(request, csrf):
        raise HTTPException(403, "Listing changes need an administrator session")
    try:
        data = json.loads(payload)
    except json.JSONDecodeError:
        return page(request, db, "edit.html", kind=kind, payload={"demonstration": True}, error="The JSON could not be read")
    data["id"] = str(data.get("id") or new_id())
    data["demonstration"] = bool(data.get("demonstration", True))
    if user.role == "HOSPITAL_ADMIN":
        owner = data.get("id") if kind == "hospital" else data.get("hospitalId")
        if owner != user.hospital_id:
            raise HTTPException(403, "You can edit listings only for your hospital")
    db.merge(Listing(id=data["id"], kind=kind, payload=json.dumps(data)))
    audit(db, user.id, "listing", data["id"], kind)
    return RedirectResponse(f"/portal/{'hospitals' if kind == 'hospital' else 'doctors' if kind == 'clinician' else 'stays'}", status_code=303)


@portal.get("/portal/requests/{request_id}", response_class=HTMLResponse)
def request_page(request_id: str, request: Request, db: Session = Depends(db_session)):
    user = user_from(request, db)
    row = db.get(CareRequest, request_id)
    if row is None or not (user.role == "PLATFORM_ADMIN" or row.hospital_id == user.hospital_id or row.patient_id == user.id):
        raise HTTPException(404, "Request not found")
    files = []
    from .models import SharedFile
    files = db.query(SharedFile).filter(SharedFile.request_id == row.id).all()
    return page(request, db, "request.html", item=request_payload(db, row), summary=json.loads(row.summary_json or "{}"), files=files, error="")


@portal.post("/portal/requests/{request_id}/quote")
def portal_quote(request_id: str, request: Request, csrf: str = Form(""), treatment: str = Form(""), currency: str = Form("USD"),
                 expires: str = Form(""), lines: str = Form(""), exclusions: str = Form(""), qualifications: str = Form(""),
                 payment: str = Form(""), extra: str = Form(""), db: Session = Depends(db_session)):
    user = user_from(request, db)
    if user.role not in {"COORDINATOR", "HOSPITAL_ADMIN", "PLATFORM_ADMIN"} or not csrf_ok(request, csrf):
        raise HTTPException(403, "Only a coordinator or administrator can issue an estimate")
    row = db.get(CareRequest, request_id)
    if row is None or (user.role != "PLATFORM_ADMIN" and row.hospital_id != user.hospital_id):
        raise HTTPException(404, "Request not found")
    parsed = []
    for line in lines.splitlines():
        if not line.strip():
            continue
        parts = [part.strip() for part in line.split("|")]
        if len(parts) < 2:
            raise HTTPException(400, "Each line needs a description and an amount, separated by |")
        parsed.append({"description": parts[0], "amount": float(parts[1]), "included": parts[2].lower() != "optional" if len(parts) > 2 else True})
    total = sum(item["amount"] for item in parsed if item["included"])
    payload = {
        "id": new_id(), "requestId": row.id, "hospitalId": row.hospital_id, "doctorId": row.doctor_id,
        "treatment": treatment.strip(), "lines": parsed, "exclusions": [item.strip() for item in exclusions.splitlines() if item.strip()],
        "currency": currency.strip() or "USD", "total": total, "expiresOn": expires.strip(),
        "paymentTerms": payment.strip(), "extraNeeded": extra.strip(), "qualifications": qualifications.strip(),
        "demonstration": True, "issuedAt": now(),
    }
    if not quote_consistent(payload):
        raise HTTPException(400, "Add a treatment name, an expiry date, and at least one included line")
    from .logic import next_status
    row.status = next_status(row.status, "ISSUE_QUOTE", user.role)
    row.updated_at = now()
    db.add(Quote(id=payload["id"], request_id=row.id, payload=json.dumps(payload)))
    audit(db, user.id, "issue_quote", row.id, payload["id"])
    return RedirectResponse(f"/portal/requests/{row.id}", status_code=303)


@portal.post("/portal/requests/{request_id}/status")
def portal_status(request_id: str, request: Request, csrf: str = Form(""), action: str = Form(""), db: Session = Depends(db_session)):
    user = user_from(request, db)
    if not csrf_ok(request, csrf):
        raise HTTPException(403, "Refresh the page and try again")
    from .api import TransitionIn, transition
    transition(request_id, TransitionIn(action=action), request, db)
    return RedirectResponse(f"/portal/requests/{request_id}", status_code=303)


@portal.post("/portal/requests/{request_id}/message")
def portal_message(request_id: str, request: Request, csrf: str = Form(""), body: str = Form(""), db: Session = Depends(db_session)):
    if not csrf_ok(request, csrf):
        raise HTTPException(403, "Refresh the page and try again")
    from .api import MessageIn, message
    message(request_id, MessageIn(body=body), request, db)
    return RedirectResponse(f"/portal/requests/{request_id}", status_code=303)


@portal.get("/portal/rates", response_class=HTMLResponse)
def rates_page(request: Request, db: Session = Depends(db_session)):
    row = db.get(Rate, "current")
    return page(request, db, "rates.html", rates=json.loads(row.payload) if row else {})


@portal.post("/portal/rates")
def rates_save(request: Request, csrf: str = Form(""), source: str = Form(""), as_of: str = Form(""), table: str = Form(""), db: Session = Depends(db_session)):
    user = user_from(request, db)
    if user.role != "PLATFORM_ADMIN" or not csrf_ok(request, csrf):
        raise HTTPException(403, "Only a platform administrator can replace rates")
    payload = {"base": "USD", "perUsd": json.loads(table), "source": source.strip(), "asOf": as_of.strip()}
    if not payload["source"]:
        raise HTTPException(400, "Name the source of these rates")
    db.merge(Rate(id="current", payload=json.dumps(payload)))
    audit(db, user.id, "rates", "current", payload["source"][:120])
    return RedirectResponse("/portal/rates", status_code=303)
