"""YuNet detection, five-landmark alignment, SFace features and cosine search."""
from io import BytesIO
from pathlib import Path
from threading import Lock
import warnings

import cv2
import numpy as np
from PIL import Image, ImageOps, UnidentifiedImageError

MODEL_NAME = "sface-2021dec"
MAX_BYTES = 5 * 1024 * 1024
MAX_PIXELS = 16_000_000
Image.MAX_IMAGE_PIXELS = MAX_PIXELS


class FaceInputError(ValueError):
    pass


def decode_image(data: bytes) -> np.ndarray:
    if not data or len(data) > MAX_BYTES:
        raise FaceInputError("图片不能为空且不能超过 5 MB")
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(BytesIO(data)) as image:
                if image.format not in {"JPEG", "PNG"}:
                    raise FaceInputError("仅支持 JPEG 和 PNG 图片")
                if image.width * image.height > MAX_PIXELS:
                    raise FaceInputError("图片像素过大，请缩小至 1600 万像素以内")
                rgb = np.asarray(ImageOps.exif_transpose(image).convert("RGB"))
        return cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR)
    except (UnidentifiedImageError, OSError, Image.DecompressionBombError, Image.DecompressionBombWarning) as exc:
        raise FaceInputError("无法解码图片，请上传有效的 JPEG 或 PNG") from exc


class FaceEngine:
    def __init__(self, model_dir: Path):
        detector = model_dir / "face_detection_yunet_2023mar.onnx"
        recognizer = model_dir / "face_recognition_sface_2021dec.onnx"
        if not detector.is_file() or not recognizer.is_file():
            raise FileNotFoundError("Run python download_models.py before starting the service")
        self.detector = cv2.FaceDetectorYN.create(str(detector), "", (320, 320), 0.9, 0.3, 5000)
        self.recognizer = cv2.FaceRecognizerSF.create(str(recognizer), "")
        self.lock = Lock()

    def extract(self, data: bytes) -> dict:
        image = decode_image(data)
        height, width = image.shape[:2]
        # Bound inference cost while retaining face landmarks at a useful resolution.
        scale = min(1.0, 1600 / max(height, width))
        if scale < 1:
            image = cv2.resize(image, (round(width * scale), round(height * scale)))
        height, width = image.shape[:2]
        with self.lock:
            self.detector.setInputSize((width, height))
            _, faces = self.detector.detect(image)
            if faces is None or len(faces) == 0:
                raise FaceInputError("未检测到人脸，请上传清晰的正面照片")
            if len(faces) != 1:
                raise FaceInputError("检测到多张人脸，请上传单人照片")
            face = faces[0]
            if min(face[2], face[3]) < 60:
                raise FaceInputError("人脸过小，请上传距离更近的清晰照片")
            aligned = self.recognizer.alignCrop(image, face)
            feature = self.recognizer.feature(aligned).flatten().copy()
        norm = float(np.linalg.norm(feature))
        if not np.isfinite(feature).all() or norm < 1e-8:
            raise FaceInputError("无法提取有效特征，请更换照片")
        feature /= norm
        return {"feature": feature.tolist(), "model": MODEL_NAME, "box": [round(float(x) / scale) for x in face[:4]]}


def match_features(feature: list[float], gallery: list, threshold: float, margin: float) -> dict:
    """Return no identity below threshold or when the top two identities are too close."""
    empty = {"personId": None, "score": 0.0, "runnerUpScore": -1.0, "ambiguous": False}
    if not gallery:
        return empty
    probe = np.asarray(feature, dtype=np.float64)
    matrix = np.asarray([candidate.feature for candidate in gallery], dtype=np.float64)
    norms = np.linalg.norm(matrix, axis=1)
    probe_norm = np.linalg.norm(probe)
    if not np.isfinite(probe).all() or not np.isfinite(matrix).all() or probe_norm < 1e-8 or (norms < 1e-8).any():
        raise FaceInputError("人脸特征无效")
    scores = np.clip(matrix @ probe / (norms * probe_norm), -1.0, 1.0)
    ranked = np.argsort(-scores, kind="stable")
    best = int(ranked[0])
    score = float(scores[best])
    second = float(scores[ranked[1]]) if len(ranked) > 1 else -1.0
    ambiguous = bool(score >= threshold and len(ranked) > 1 and score - second < margin)
    return {
        "personId": gallery[best].id if score >= threshold and not ambiguous else None,
        "score": score,
        "runnerUpScore": second,
        "ambiguous": ambiguous,
    }
