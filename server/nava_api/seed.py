import json
import os
from pathlib import Path
from .db import open_session
from .models import Listing, Rate, User
from .security import hash_password, new_id


def catalog_path() -> Path:
    return Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "assets" / "demo-catalog.json"


def seed():
    if os.environ.get("SEED_DEMO", "1") != "1":
        return
    db = open_session()
    try:
        if db.query(User).first():
            return
        catalog = json.loads(catalog_path().read_text(encoding="utf-8"))
        kinds = {"hospitals": "hospital", "clinicians": "clinician", "stays": "stay"}
        for kind, stored in kinds.items():
            for item in catalog[kind]:
                db.add(Listing(id=item["id"], kind=stored, payload=json.dumps(item)))
        db.add(Rate(id="current", payload=json.dumps(catalog["rates"])))
        accounts = [
            ("admin@demo.nava.invalid", "DEMO_ADMIN_PASSWORD", "demo-admin-change-me", "PLATFORM_ADMIN", "", "Demo platform admin"),
            ("coordinator@demo.nava.invalid", "DEMO_COORDINATOR_PASSWORD", "demo-coordinator-change-me", "COORDINATOR", "h-sepehr", "هماهنگ‌کننده سپهر"),
            ("doctor@demo.nava.invalid", "DEMO_DOCTOR_PASSWORD", "demo-doctor-change-me", "DOCTOR", "h-sepehr", "پزشک نمونه سپهر"),
            ("cedar@demo.nava.invalid", "DEMO_CEDAR_PASSWORD", "demo-cedar-change-me", "COORDINATOR", "h-zayandeh", "هماهنگ‌کننده زاینده‌رود"),
        ]
        for email, env, default, role, hospital, name in accounts:
            db.add(User(id=new_id(), email=email, password_hash=hash_password(os.environ.get(env, default)), role=role, hospital_id=hospital, display_name=name))
        db.commit()
    finally:
        db.close()
