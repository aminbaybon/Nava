from sqlalchemy import String, Text, Integer
from sqlalchemy.orm import Mapped, mapped_column
from .db import Base


class User(Base):
    __tablename__ = "users"
    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    email: Mapped[str] = mapped_column(String(200), unique=True)
    password_hash: Mapped[str] = mapped_column(String(200))
    role: Mapped[str] = mapped_column(String(32))
    hospital_id: Mapped[str] = mapped_column(String(64), default="")
    display_name: Mapped[str] = mapped_column(String(120), default="")


class Listing(Base):
    __tablename__ = "listings"
    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    kind: Mapped[str] = mapped_column(String(32))
    payload: Mapped[str] = mapped_column(Text)


class CareRequest(Base):
    __tablename__ = "requests"
    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    patient_id: Mapped[str] = mapped_column(String(64))
    hospital_id: Mapped[str] = mapped_column(String(64))
    doctor_id: Mapped[str] = mapped_column(String(64), default="")
    pathway: Mapped[str] = mapped_column(String(16))
    note: Mapped[str] = mapped_column(Text, default="")
    status: Mapped[str] = mapped_column(String(40))
    summary_json: Mapped[str] = mapped_column(Text, default="{}")
    document_ids: Mapped[str] = mapped_column(Text, default="[]")
    created_at: Mapped[str] = mapped_column(String(40))
    updated_at: Mapped[str] = mapped_column(String(40))


class Quote(Base):
    __tablename__ = "quotes"
    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    request_id: Mapped[str] = mapped_column(String(64))
    payload: Mapped[str] = mapped_column(Text)


class Message(Base):
    __tablename__ = "messages"
    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    request_id: Mapped[str] = mapped_column(String(64))
    actor: Mapped[str] = mapped_column(String(40))
    sender: Mapped[str] = mapped_column(String(120))
    body: Mapped[str] = mapped_column(Text)
    created_at: Mapped[str] = mapped_column(String(40))


class SharedFile(Base):
    __tablename__ = "files"
    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    request_id: Mapped[str] = mapped_column(String(64))
    name: Mapped[str] = mapped_column(String(200))
    mime: Mapped[str] = mapped_column(String(80))
    path: Mapped[str] = mapped_column(String(400))
    size: Mapped[int] = mapped_column(Integer)


class Audit(Base):
    __tablename__ = "audit"
    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    actor_id: Mapped[str] = mapped_column(String(64))
    action: Mapped[str] = mapped_column(String(80))
    target: Mapped[str] = mapped_column(String(80))
    detail: Mapped[str] = mapped_column(Text, default="")
    created_at: Mapped[str] = mapped_column(String(40))


class Rate(Base):
    __tablename__ = "rates"
    id: Mapped[str] = mapped_column(String(16), primary_key=True)
    payload: Mapped[str] = mapped_column(Text)
