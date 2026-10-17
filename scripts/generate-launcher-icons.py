"""Resize the supplied SafarVPN artwork into Android launcher assets.

Usage: python scripts/generate-launcher-icons.py path/to/original.png
Requires Pillow. Keeps the original image intact in the adaptive icon's
central 72dp viewport; launcher masks may choose a circle or rounded square.
"""

from pathlib import Path
import shutil
import sys

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
source = Image.open(sys.argv[1]).convert("RGBA")
if source.width != source.height:
    raise ValueError("Launcher artwork must be square; do not crop the source")

for density, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96),
                      ("xxhdpi", 144), ("xxxhdpi", 192)]:
    folder = RES / f"mipmap-{density}"
    icon = source.resize((size, size), Image.Resampling.LANCZOS)
    for name in ["ic_launcher", "ic_launcher_round"]:
        icon.save(folder / f"{name}.webp", lossless=True)
    full = size * 108 // 48
    viewport = size * 72 // 48
    foreground = Image.new("RGBA", (full, full))
    artwork = source.resize((viewport, viewport), Image.Resampling.LANCZOS)
    foreground.paste(artwork, ((full - viewport) // 2,) * 2)
    foreground.save(folder / "ic_launcher_foreground.webp", lossless=True)

out = ROOT / "artifacts"
out.mkdir(exist_ok=True)
shutil.copyfile(sys.argv[1], out / "SafarVPN-1.4.21-icon-source.png")
preview_size = 384
preview = Image.new("RGB", (preview_size * 3, preview_size + 40), "#dddddd")
icon = source.resize((preview_size, preview_size), Image.Resampling.LANCZOS)
draw = ImageDraw.Draw(preview)
for index, label in enumerate(["Original / square", "Adaptive circle", "Adaptive rounded square"]):
    mask = Image.new("L", (preview_size, preview_size), 0)
    mask_draw = ImageDraw.Draw(mask)
    if index == 1:
        mask_draw.ellipse((0, 0, preview_size - 1, preview_size - 1), fill=255)
    elif index == 2:
        mask_draw.rounded_rectangle((0, 0, preview_size - 1, preview_size - 1), radius=88, fill=255)
    else:
        mask.paste(255, (0, 0, preview_size, preview_size))
    preview.paste(icon, (index * preview_size, 0), mask)
    draw.text((index * preview_size + 12, preview_size + 12), label, fill="black")
preview.save(out / "SafarVPN-1.4.21-icon-preview.png")
