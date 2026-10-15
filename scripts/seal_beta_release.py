"""Seal acceptance evidence for the exact release JAR. Does not install or publish."""
import argparse
import hashlib
import io
import json
import re
import shutil
import tomllib
import xml.etree.ElementTree as ET
import zipfile
from datetime import datetime, timezone
from pathlib import Path

p = argparse.ArgumentParser(description=__doc__)
p.add_argument("--review", type=Path, required=True)
p.add_argument("--jar", type=Path, required=True)
p.add_argument("--build-log", type=Path, required=True)
p.add_argument("--minecraft", required=True)
p.add_argument("--loader", choices=["neoforge", "forge"], required=True)
a = p.parse_args()
root = Path(__file__).resolve().parents[1]
version = next(line.split("=", 1)[1] for line in (root / "gradle.properties").read_text().splitlines() if line.startswith("mod_version="))
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))
def require(ok, message):
    if not ok:
        raise SystemExit(message)

build_log = a.build_log.read_text(encoding="utf-8", errors="replace")
require("BUILD SUCCESSFUL" in build_log, "Canonical build did not pass")
require("All 29 required tests passed" in build_log, "Required GameTest coverage is incomplete")
web = read(a.review / "web/result.json")
binding = read(a.review / "web/source-binding.json")
require(web["status"] == "passed" and web["headless"] and not web["systemClipboardUsed"]
        and not web["errors"] and web["renderedStatements"] == 1000, "Full-bank Web acceptance did not pass")
require(binding["testResultSha256"] == sha(a.review / "web/result.json"), "Web result binding is stale")
for relative, expected in binding["sourceHashes"].items():
    require(sha(root / relative) == expected, f"Web source drift: {relative}")
tasks = ["test", "officialRepositoryContractTest", "officialQuestionBankQualityTest"]
tasks += [f"officialQuestionBankTestP{i:03}ToP{i+49:03}" for i in range(1, 501, 50)]
counts, xml_hashes = {}, {}
for task in tasks:
    reports = list((root / "build/test-results" / task).glob("TEST-*.xml"))
    require(bool(reports), f"Missing results: {task}")
    counts[task] = 0
    for report in reports:
        tree = ET.parse(report).getroot()
        require(all(int(tree.get(k, "0")) == 0 for k in ("failures", "errors", "skipped")), f"Non-passing tests: {report}")
        counts[task] += int(tree.get("tests", "0"))
        dest = a.review / "junit" / task / report.name
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(report, dest)
        xml_hashes[dest.relative_to(a.review).as_posix()] = sha(dest)
require(counts["test"] >= 998 and counts["officialRepositoryContractTest"] == 67
        and sum(counts[t] for t in tasks[3:]) == 4342, "Required full-bank JVM coverage is incomplete")

native = root / "build/algocraft-ide-stress"
result, launch, exit_receipt = [read(native / f"{name}.json") for name in ("result", "launch", "exit")]
require(result["status"] == "passed" and result["stressPasses"] == 10
        and result["allScenariosPassed"] and result["cleanupFailureCount"] == 0, "Native acceptance did not pass")
require(result["sessionId"] == launch["sessionId"] == exit_receipt["sessionId"]
        and exit_receipt["exitCode"] == 0 and exit_receipt["runtimeExitCount"] == 1, "Native session did not exit cleanly")
for relative, expected in launch["sourceHashes"].items():
    require(sha(root / relative) == expected, f"Native source drift: {relative}")
for receipt in [result, *[read(root / "build/algocraft-bank-multiplayer" / f"{role}-result.json") for role in ("A", "B")]]:
    isolation = receipt["desktopIsolation"]
    require(all(isolation[k] for k in ("enabled", "hidden", "unfocused", "cursorFree", "windowed", "silentDevice", "clipboardIsolated"))
            and isolation["violationCount"] == 0, "Desktop isolation failed")
multi = root / "build/algocraft-bank-multiplayer"
require(all(read(multi / f"{role}-result.json")["passed"] for role in ("A", "B", "server")), "Multiplayer acceptance failed")
require(read(multi / "configuration-restored.json")["allRestored"], "Multiplayer configuration was not restored")
require("BUILD SUCCESSFUL" in (a.review / "runBankServer.log").read_text(errors="replace"), "Dedicated server did not exit cleanly")
for role in ("A", "B"):
    require(read(multi / f"BankClient{role}/exit.json")["exitCode"] == 0, f"Client {role} did not exit cleanly")
with zipfile.ZipFile(a.jar) as z:
    require(z.testzip() is None, "JAR integrity failed")
    meta = tomllib.loads(z.read(f"META-INF/{'neoforge.mods.toml' if a.loader == 'neoforge' else 'mods.toml'}").decode())
    require(meta["mods"][0]["modId"] == "algocraft" and meta["mods"][0]["version"] == version, "Wrong mod identity")
    deps = {v["modId"]: v for v in meta["dependencies"]["algocraft"]}
    require(a.minecraft in deps["minecraft"]["versionRange"] and a.loader in deps, "Wrong runtime metadata")
    require("assets/algocraft/compiler/ecj.jar" in z.namelist(), "Bundled compiler missing")
    for asset in (root / "src/main/resources/assets/algocraft").rglob("*"):
        if asset.is_file():
            name = asset.relative_to(root / "src/main/resources").as_posix()
            require(z.read(name) == asset.read_bytes(), f"Packaged asset differs: {name}")
    for compiled in (root / "build/classes/java/main").rglob("*.class"):
        name = compiled.relative_to(root / "build/classes/java/main").as_posix()
        if a.loader == "neoforge":
            require(z.read(name) == compiled.read_bytes(), f"Packaged class differs: {name}")
        require(int.from_bytes(z.read(name)[6:8], "big") <= (61 if a.loader == "forge" else 65), f"Incompatible bytecode: {name}")
    with zipfile.ZipFile(io.BytesIO(z.read("assets/algocraft/official-bank.zip"))) as bank:
        require(len([n for n in bank.namelist() if re.fullmatch(r"p\d+\.json", n)]) == 500, "Bundled bank incomplete")
        for name in bank.namelist():
            if name.endswith("/"):
                continue
            data = (root / "question_bank/official" / name).read_bytes()
            if name.endswith(".json"):
                data = data.replace(b"\r\n", b"\n").replace(b"\r", b"\n")
            require(bank.read(name) == data, f"Bundled bank differs: {name}")
    if a.loader == "forge":
        require("algocraft.unattended.refmap.json" in z.namelist()
                and b"MixinConfigs: algocraft.unattended.mixins.json" in z.read("META-INF/MANIFEST.MF"), "Forge production mixin metadata missing")
out_jar = a.review / f"algocraft-{a.minecraft}-{version}.jar"
shutil.copyfile(a.jar, out_jar)
for source, name in [(native, "native"), (multi, "multiplayer")]:
    shutil.copytree(source, a.review / name, dirs_exist_ok=True)
source_hashes = {f.relative_to(root).as_posix(): sha(f) for f in (root / "src/main").rglob("*") if f.is_file()}
receipt = {"status": "automated-acceptance-passed", "sealedAtUtc": datetime.now(timezone.utc).isoformat(),
           "minecraft": a.minecraft, "loader": a.loader, "version": version, "jar": str(out_jar.resolve()),
           "jarSha256": sha(out_jar), "junit": counts, "junitTotal": sum(counts.values()), "junitXmlHashes": xml_hashes,
           "nativeChecks": len(result["checks"]), "nativeSessionId": result["sessionId"],
           "gameTests": 29, "webChecks": len(web["checks"]), "webRenderedStatements": web["renderedStatements"],
           "sourceHashes": source_hashes, "buildLogSha256": sha(a.build_log),
           "nativeReceiptHashes": {name: sha(native / f"{name}.json") for name in ("result", "launch", "exit")},
           "multiplayerReceiptHashes": {name: sha(multi / f"{name}.json") for name in ("A-result", "B-result", "server-result", "topology", "configuration-restored")},
           "webSourceBindingSha256": sha(a.review / "web/source-binding.json"),
           "runtimeGuardSha256": sha(root / "gradle/algocraft-tests.gradle")}
(a.review / "final-receipt.json").write_text(json.dumps(receipt, indent=2), encoding="utf-8")
print(json.dumps({k: receipt[k] for k in ("status", "jar", "jarSha256", "junitTotal", "nativeChecks")}))
