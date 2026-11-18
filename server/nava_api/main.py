from pathlib import Path
import os
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from .api import api
from .db import create_all
from .portal import portal
from .seed import seed

ROOT = Path(__file__).resolve().parents[1]


def prepare():
    os.makedirs(ROOT / "data" / "files", exist_ok=True)
    create_all()
    seed()


def create_app() -> FastAPI:
    app = FastAPI(title="Nava shared care", docs_url="/api/docs")
    app.mount("/static", StaticFiles(directory=ROOT / "static"), name="static")
    app.include_router(api)
    app.include_router(portal)

    @app.on_event("startup")
    def startup():
        prepare()

    return app


app = create_app()
