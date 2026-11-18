import hashlib
import hmac
import os
import uuid
from itsdangerous import BadSignature, SignatureExpired, URLSafeTimedSerializer

SECRET = lambda: os.environ.get("SECRET", "dev-only-change-me")


def hash_password(password: str) -> str:
    salt = os.urandom(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, 200_000)
    return salt.hex() + ":" + digest.hex()


def verify_password(password: str, stored: str) -> bool:
    salt_hex, digest_hex = stored.split(":", 1)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(salt_hex), 200_000)
    return hmac.compare_digest(digest.hex(), digest_hex)


def serializer():
    return URLSafeTimedSerializer(SECRET(), salt="nava-session")


def issue_token(user_id: str, role: str) -> str:
    return serializer().dumps({"uid": user_id, "role": role})


def read_token(token: str, max_age: int = 60 * 60 * 12):
    try:
        return serializer().loads(token, max_age=max_age)
    except (BadSignature, SignatureExpired):
        return None


def new_id() -> str:
    return str(uuid.uuid4())
