"""Download official OpenCV Zoo models atomically and verify their SHA-256."""
import hashlib
from pathlib import Path
import urllib.request

MODELS = {
    "face_detection_yunet_2023mar.onnx": (
        "face_detection_yunet", "8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4"
    ),
    "face_recognition_sface_2021dec.onnx": (
        "face_recognition_sface", "0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79"
    ),
}


def sha256(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def main():
    folder = Path(__file__).resolve().parent / "models"
    folder.mkdir(exist_ok=True)
    for name, (model, expected) in MODELS.items():
        dest = folder / name
        if dest.is_file() and sha256(dest) == expected:
            print(f"Verified: {name}")
            continue
        url = f"https://media.githubusercontent.com/media/opencv/opencv_zoo/main/models/{model}/{name}"
        temporary = dest.with_suffix(".download")
        try:
            print(f"Downloading: {name}")
            with urllib.request.urlopen(url, timeout=120) as response, temporary.open("wb") as output:
                while chunk := response.read(1024 * 1024):
                    output.write(chunk)
            if sha256(temporary) != expected:
                raise RuntimeError(f"Checksum mismatch: {name}; do not use this file")
            temporary.replace(dest)
        finally:
            temporary.unlink(missing_ok=True)
    print("Both models are ready.")


if __name__ == "__main__":
    main()
