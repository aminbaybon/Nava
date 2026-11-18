"""Mirrors the Kotlin RequestFlow table. Keep the two in step."""
STAFF = {"COORDINATOR", "HOSPITAL_ADMIN", "PLATFORM_ADMIN"}
TERMINAL = {"ACCEPTED", "DECLINED", "CANCELLED", "EXPIRED"}
PATIENT_CANCEL = {"DRAFT", "SUBMITTED", "UNDER_REVIEW", "MORE_INFORMATION_REQUIRED", "QUOTATION_RECEIVED"}
SUMMARY_KEYS = {"concern", "history", "treatments", "results", "conditions", "missing", "questions", "englishNote", "englishIsMachine", "assembledAt"}


def next_status(status: str, action: str, role: str) -> str:
    staff = role in STAFF
    if role == "PATIENT" and status == "DRAFT" and action == "SUBMIT":
        return "SUBMITTED"
    if role == "PATIENT" and status == "MORE_INFORMATION_REQUIRED" and action == "SUBMIT":
        return "SUBMITTED"
    if role == "PATIENT" and status == "QUOTATION_RECEIVED" and action == "ACCEPT":
        return "ACCEPTED"
    if role == "PATIENT" and status == "QUOTATION_RECEIVED" and action == "DECLINE":
        return "DECLINED"
    if role == "PATIENT" and action == "CANCEL" and status in PATIENT_CANCEL:
        return "CANCELLED"
    if staff and status == "SUBMITTED" and action == "START_REVIEW":
        return "UNDER_REVIEW"
    if staff and status in {"SUBMITTED", "UNDER_REVIEW"} and action == "NEED_INFO":
        return "MORE_INFORMATION_REQUIRED"
    if staff and status in {"UNDER_REVIEW", "MORE_INFORMATION_REQUIRED"} and action == "ISSUE_QUOTE":
        return "QUOTATION_RECEIVED"
    if staff and action == "CANCEL" and status not in TERMINAL:
        return "CANCELLED"
    if role == "SYSTEM" and status == "QUOTATION_RECEIVED" and action == "EXPIRE":
        return "EXPIRED"
    raise ValueError(f"Transition {action} from {status} is not allowed for {role}")


def quote_consistent(payload: dict) -> bool:
    if not payload.get("currency") or not payload.get("treatment") or not payload.get("expiresOn"):
        return False
    lines = payload.get("lines") or []
    if any(not str(line.get("description", "")).strip() for line in lines):
        return False
    total = float(payload.get("total") or 0)
    included = sum(float(line.get("amount") or 0) for line in lines if line.get("included", True))
    return abs(included - total) < 0.02


def clean_summary(raw: dict | None) -> dict:
    if not raw:
        return {}
    return {key: raw[key] for key in SUMMARY_KEYS if key in raw}


def actor_for(role: str) -> str:
    return {
        "PATIENT": "PATIENT",
        "COORDINATOR": "HOSPITAL_COORDINATOR",
        "HOSPITAL_ADMIN": "HOSPITAL_COORDINATOR",
        "DOCTOR": "PHYSICIAN",
        "PLATFORM_ADMIN": "SYSTEM",
    }[role]
