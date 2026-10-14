"""Freeze the already confirmed local voice corpus before any GigaAM inference."""
import hashlib
import json
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OLD = ROOT / "android/app/build/ui-review/whisper-audit-078"
DOCTOR = "Напомни завтра утром сходить к врачу и купить бумаги"
SHOW = "Покажи напоминания на завтра в восемь вечера"
CHECK = "Напомни завтра в десять проверить документы в синей папке"
CASES = [
    ("greeting", OLD / "greeting.wav", "Привет", "primary"),
    ("repeat", OLD / "repeat.wav", "Привет привет очень очень важно", "primary"),
    *[(f"capture{i}", OLD / f"capture{i}.wav", DOCTOR, "primary") for i in range(3)],
    ("date", OLD / "date.wav", "Завтра в десять двадцать пять", "primary"),
    ("show", OLD / "show-reminders/last_processed.wav", SHOW, "primary"),
    ("check", OLD / "check-documents/last_processed.wav", CHECK, "primary"),
    ("count", ROOT / "android/app/build/ui-review/repetition-audit-20261012/count.wav", "Раз два три", "primary"),
    ("raw-show", OLD / "show-reminders/last_raw.wav", SHOW, "raw-control"),
    ("raw-check", OLD / "check-documents/last_raw.wav", CHECK, "raw-control"),
]

def main():
    result = []
    for name, path, reference, group in CASES:
        with wave.open(str(path), "rb") as audio:
            assert (audio.getnchannels(), audio.getsampwidth(), audio.getframerate()) == (1, 2, 16000)
            duration = audio.getnframes() / 16000
        result.append(dict(id=name, path=path.relative_to(ROOT).as_posix(), expected=reference,
            group=group, seconds=duration, sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
    destination = ROOT / ".gigaam-cache/corpus.json"
    destination.parent.mkdir(exist_ok=True)
    if destination.exists():
        assert json.loads(destination.read_text(encoding="utf-8")) == result, "Existing frozen corpus differs"
    else:
        destination.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Frozen {len(result)} inputs ({sum(r['group']=='primary' for r in result)} primary), no model execution")

if __name__ == "__main__":
    main()
