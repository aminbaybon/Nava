"""SQLite by default. Set DATABASE_URL to a PostgreSQL URL when that server is available."""
import os
from pathlib import Path
from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, sessionmaker

_engine = None
_session = None


def database_url() -> str:
    raw = os.environ.get("DATABASE_URL")
    if raw:
        return raw
    path = Path(__file__).resolve().parents[1] / "data" / "nava.db"
    path.parent.mkdir(parents=True, exist_ok=True)
    return "sqlite:///" + path.as_posix()


def engine():
    global _engine, _session
    if _engine is None:
        url = database_url()
        connect = {"check_same_thread": False} if url.startswith("sqlite") else {}
        _engine = create_engine(url, connect_args=connect, future=True)
        _session = sessionmaker(bind=_engine, autoflush=False, autocommit=False, future=True)
    return _engine


def reset_engine():
    global _engine, _session
    if _engine is not None:
        _engine.dispose()
    _engine = None
    _session = None


class Base(DeclarativeBase):
    pass


def session():
    engine()
    db = _session()
    try:
        yield db
        db.commit()
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()


def open_session():
    engine()
    return _session()


def create_all():
    from . import models  # noqa: F401
    Base.metadata.create_all(engine())
