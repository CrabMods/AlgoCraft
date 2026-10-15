"""Validate AlgoCraft's public loader update feed."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
feed = json.loads((ROOT / "update.json").read_text(encoding="utf-8"))
version = next(line.split("=", 1)[1] for line in (ROOT / "gradle.properties").read_text().splitlines() if line.startswith("mod_version="))
assert feed["homepage"] == "https://github.com/crabsatellite/AlgoCraft"
for mc in ("1.20.1", "1.21.1"):
    versions = feed[mc]
    assert versions and all(isinstance(version, str) and message for version, message in versions.items())
    assert feed["promos"][f"{mc}-latest"] in versions
    assert feed["promos"][f"{mc}-recommended"] in versions
    assert feed["promos"][f"{mc}-recommended"] == version
assert not any(key.endswith("-recommended") and value != version for key, value in feed["promos"].items())
print("Update JSON passed: both Minecraft versions have latest and recommended entries.")
