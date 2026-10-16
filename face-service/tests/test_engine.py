from io import BytesIO
from types import SimpleNamespace
import numpy as np
import pytest
from PIL import Image
from app.engine import FaceEngine, FaceInputError, decode_image, match_features


def vector(index):
    result = [0.0] * 128
    result[index] = 1.0
    return result


def candidate(identity, feature):
    return SimpleNamespace(id=identity, feature=feature)


def test_same_identity_matches_and_orthogonal_identity_is_unknown():
    gallery = [candidate(1, vector(0))]
    assert match_features(vector(0), gallery, 0.45, 0.05)["personId"] == 1
    assert match_features(vector(1), gallery, 0.45, 0.05)["personId"] is None


def test_ambiguous_top_two_identities_are_rejected():
    gallery = [candidate(1, vector(0)), candidate(2, vector(0))]
    result = match_features(vector(0), gallery, 0.45, 0.05)
    assert result["personId"] is None and result["ambiguous"]


def test_empty_gallery_returns_unknown():
    assert match_features(vector(0), [], 0.45, 0.05)["personId"] is None


@pytest.mark.parametrize("bad", [[0.0] * 128, [float("nan")] * 128])
def test_invalid_features_are_rejected(bad):
    with pytest.raises(FaceInputError):
        match_features(bad, [candidate(1, vector(0))], 0.45, 0.05)


def test_decode_rejects_fake_images_and_overlarge_uploads():
    for content in (b"not an image", b"", b"x" * (5 * 1024 * 1024 + 1)):
        with pytest.raises(FaceInputError):
            decode_image(content)


def test_decode_returns_bgr_pixels():
    buffer = BytesIO()
    Image.new("RGB", (100, 100), (255, 0, 0)).save(buffer, format="PNG")
    image = decode_image(buffer.getvalue())
    np.testing.assert_array_equal(image[0, 0], [0, 0, 255])


def test_multi_face_and_no_face_are_rejected():
    # Isolate the detector outputs to verify the single-face policy deterministically.
    class Detector:
        def __init__(self, faces): self.faces = faces
        def setInputSize(self, size): pass
        def detect(self, image): return None, self.faces
    from threading import Lock
    engine = object.__new__(FaceEngine)
    engine.lock = Lock()
    buffer = BytesIO()
    Image.new("RGB", (100, 100)).save(buffer, format="PNG")
    for faces in (None, np.zeros((2, 15))):
        engine.detector = Detector(faces)
        with pytest.raises(FaceInputError): engine.extract(buffer.getvalue())
