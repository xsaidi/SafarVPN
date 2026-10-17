"""Check packaged launcher resources against source assets after R8 renaming."""

from hashlib import sha256
from io import BytesIO
import json
from pathlib import Path
import re
import subprocess
import sys
from zipfile import ZipFile

from PIL import Image

root = Path(__file__).resolve().parents[1]
apk = Path(sys.argv[1]).resolve()
aapt = root / ".build-tools/android-sdk/build-tools/36.0.0/aapt2.exe"

def dump(*arguments):
    return subprocess.check_output([str(aapt), "dump", *arguments], text=True, encoding="utf-8")

table = dump("resources", str(apk))
resources = {}
current = None
for line in table.splitlines():
    match = re.search(r"resource (0x[0-9a-f]+) (\S+)", line)
    if match:
        current = match[2]
        resources[current] = {"id": match[1], "files": {}}
    match = re.search(r"\(([^)]*)\) \(file\) (\S+)", line)
    if match and current:
        resources[current]["files"][match[1]] = match[2]

evidence = {"bitmapResources": [], "adaptiveResources": []}
with ZipFile(apk) as archive:
    for name in ["ic_launcher", "ic_launcher_round", "ic_launcher_foreground"]:
        resource = resources[f"mipmap/{name}"]
        for density in ["mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]:
            packaged_path = resource["files"][density]
            packaged = archive.read(packaged_path)
            local = (root / f"app/src/main/res/mipmap-{density}/{name}.webp").read_bytes()
            assert packaged == local, f"Packaged bitmap mismatch: {name}/{density}"
            image = Image.open(BytesIO(packaged))
            evidence["bitmapResources"].append({"name": name, "density": density,
                "apkPath": packaged_path, "size": list(image.size), "sha256": sha256(packaged).hexdigest()})
    for name in ["ic_launcher", "ic_launcher_round"]:
        path = resources[f"mipmap/{name}"]["files"]["anydpi"]
        xml = dump("xmltree", str(apk), "--file", path)
        assert "adaptive-icon" in xml and "monochrome" not in xml
        assert resources["drawable/ic_launcher_background"]["id"] in xml
        assert resources["mipmap/ic_launcher_foreground"]["id"] in xml
        evidence["adaptiveResources"].append({"name": name, "apkPath": path,
            "foregroundAndBackgroundVerified": True, "monochrome": False})
    manifest = dump("xmltree", str(apk), "--file", "AndroidManifest.xml")
    assert resources["mipmap/ic_launcher"]["id"] in manifest
    assert resources["mipmap/ic_launcher_round"]["id"] in manifest
evidence["manifestIconsVerified"] = True
print(json.dumps(evidence, indent=2))
