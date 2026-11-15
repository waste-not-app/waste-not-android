#!/usr/bin/env python3
"""Regenerate Android branding from docs/logo using Pillow (python3 -m pip install Pillow)."""

from pathlib import Path

from PIL import Image, ImageChops, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
LOGOS = ROOT / "docs/logo"
PINE = "#0C3023"
SCALE = 4


def save_webp(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="WEBP", lossless=True, exact=True, method=6)


def load_app_icon() -> Image.Image:
    image = Image.open(LOGOS / "wastenot-app-icon.png").convert("RGBA")
    alpha = image.getchannel("A")
    # The export has stray alpha 1 pixels at the canvas edges. Exclude only
    # this near-invisible noise, retaining the supplied squircle's soft edge.
    alpha = alpha.point(lambda value: 0 if value < 8 else value)
    image.putalpha(alpha)
    bounds = alpha.getbbox()
    if bounds is None:
        raise ValueError("The app icon contains no visible artwork")
    image = image.crop(bounds)
    side = max(image.size)
    square = Image.new("RGBA", (side, side))
    square.alpha_composite(image, ((side - image.width) // 2, (side - image.height) // 2))
    return square


def mark_canvas(mark: Image.Image, canvas_dp: int, width_dp: int) -> Image.Image:
    width = width_dp * SCALE
    height = round(width * mark.height / mark.width)
    artwork = mark.resize((width, height), Image.Resampling.LANCZOS)
    side = canvas_dp * SCALE
    canvas = Image.new("RGBA", (side, side))
    canvas.alpha_composite(artwork, ((side - width) // 2, (side - height) // 2))
    return canvas


def main() -> None:
    icon = load_app_icon()
    mark = Image.open(LOGOS / "wastenot-w-mark.png").convert("RGBA")
    drawables = RES / "drawable-nodpi"
    save_webp(icon.resize((768, 768), Image.Resampling.LANCZOS), drawables / "wastenot_brand_icon.webp")
    # Keep the original 773:546 aspect ratio and alpha, without a background.
    save_webp(mark, drawables / "wastenot_brand_mark.webp")

    # The entire 52 x 37dp mark fits inside the adaptive icon's 66dp safe circle.
    foreground = mark_canvas(mark, canvas_dp=108, width_dp=52)
    save_webp(foreground, drawables / "wastenot_launcher_foreground.webp")
    monochrome = Image.new("RGBA", foreground.size, "white")
    monochrome.putalpha(foreground.getchannel("A"))
    save_webp(monochrome, drawables / "wastenot_launcher_monochrome.webp")

    # Keep a Pine medallion within Android 12's 192dp circular safe region.
    # This preserves contrast for the cream ribbon on the light splash screen.
    splash = Image.new("RGBA", (288 * SCALE, 288 * SCALE))
    inset = (288 - 184) * SCALE // 2
    ImageDraw.Draw(splash).ellipse(
        (inset, inset, splash.width - inset - 1, splash.height - inset - 1),
        fill=PINE,
    )
    splash.alpha_composite(mark_canvas(mark, canvas_dp=288, width_dp=140))
    save_webp(splash, drawables / "wastenot_splash_mark.webp")

    for density, side in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        directory = RES / f"mipmap-{density}"
        save_webp(icon.resize((side, side), Image.Resampling.LANCZOS), directory / "ic_launcher.webp")

        # Crop the complete supplied artwork with a true circular legacy mask.
        large_side = side * SCALE
        round_icon = Image.new("RGBA", (large_side, large_side), PINE)
        round_icon.alpha_composite(icon.resize(round_icon.size, Image.Resampling.LANCZOS))
        circle = Image.new("L", round_icon.size)
        ImageDraw.Draw(circle).ellipse((0, 0, large_side - 1, large_side - 1), fill=255)
        round_icon.putalpha(ImageChops.multiply(round_icon.getchannel("A"), circle))
        save_webp(round_icon.resize((side, side), Image.Resampling.LANCZOS), directory / "ic_launcher_round.webp")


if __name__ == "__main__":
    main()
