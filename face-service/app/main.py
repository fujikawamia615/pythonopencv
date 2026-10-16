import hmac
import logging
import os
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Annotated

import cv2
from fastapi import Depends, FastAPI, File, Header, HTTPException, UploadFile
from pydantic import BaseModel, Field, FiniteFloat
from .engine import FaceEngine, FaceInputError, MAX_BYTES, match_features

Feature = Annotated[list[FiniteFloat], Field(min_length=128, max_length=128)]


class Candidate(BaseModel):
    id: int = Field(gt=0)
    feature: Feature


class MatchInput(BaseModel):
    feature: Feature
    gallery: list[Candidate] = Field(max_length=10000)
    threshold: float = Field(ge=0, le=1)
    margin: float = Field(ge=0, le=1)


@asynccontextmanager
async def lifespan(app: FastAPI):
    key = os.environ.get("FACE_SERVICE_KEY", "")
    if len(key) < 16:
        raise RuntimeError("FACE_SERVICE_KEY must contain at least 16 characters")
    app.state.service_key = key
    app.state.engine = None
    try:
        model_dir = Path(os.environ.get("MODEL_DIR", str(Path(__file__).resolve().parent.parent / "models")))
        app.state.engine = FaceEngine(model_dir)
    except (FileNotFoundError, cv2.error):
        logging.exception("Face models unavailable; download models and restart")
    yield


app = FastAPI(title="FaceGate · 人脸识别服务", version="1.0.0", lifespan=lifespan, docs_url=None, redoc_url=None)


def authorize(x_service_key: Annotated[str | None, Header()] = None):
    if not x_service_key or not hmac.compare_digest(x_service_key, app.state.service_key):
        raise HTTPException(401, "服务身份验证失败")


@app.get("/health")
def health():
    if app.state.engine is None:
        raise HTTPException(503, "模型未就绪，请运行 download_models.py 后重启")
    return {"status": "ok", "model": "sface-2021dec"}


@app.post("/extract", dependencies=[Depends(authorize)])
def extract(file: UploadFile = File(...)):
    if app.state.engine is None:
        raise HTTPException(503, "模型未就绪")
    data = file.file.read(MAX_BYTES + 1)
    try:
        return app.state.engine.extract(data)
    except FaceInputError as exc:
        raise HTTPException(422, str(exc)) from exc
    except cv2.error as exc:
        logging.exception("OpenCV inference failed")
        raise HTTPException(503, "模型推理失败，请检查图片或服务日志") from exc


@app.post("/match", dependencies=[Depends(authorize)])
def match(body: MatchInput):
    if len({candidate.id for candidate in body.gallery}) != len(body.gallery):
        raise HTTPException(422, "候选人员编号不能重复")
    try:
        return match_features(body.feature, body.gallery, body.threshold, body.margin)
    except FaceInputError as exc:
        raise HTTPException(422, str(exc)) from exc
