import os
import shutil
from PIL import Image, ImageDraw

image_path = r"C:\Users\varma\.gemini\antigravity\brain\47a6f55c-ea39-4ffb-845e-441357632b79\gravity_installer_icon_1782735533205.jpg"
project_dir = r"C:\Users\varma\OneDrive\Desktop\Projects\gravityinstaller"

img = Image.open(image_path).convert("RGBA")

# Mipmap sizes
sizes = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192
}

modules = ["app", "tv"]

for module in modules:
    res_dir = os.path.join(project_dir, module, "src", "main", "res")
    
    if not os.path.exists(res_dir):
        continue

    # 1. Save drawable-nodpi
    nodpi_dir = os.path.join(res_dir, "drawable-nodpi")
    os.makedirs(nodpi_dir, exist_ok=True)
    img_1024 = img.resize((1024, 1024), Image.Resampling.LANCZOS)
    img_1024.save(os.path.join(nodpi_dir, "ic_launcher_foreground.png"), "PNG")
    img_1024.save(os.path.join(nodpi_dir, "ic_launcher_monochrome.png"), "PNG")

    # 2. Delete XMLs if they exist
    xml1 = os.path.join(res_dir, "drawable", "ic_launcher_foreground.xml")
    xml2 = os.path.join(res_dir, "drawable", "ic_launcher_monochrome.xml")
    if os.path.exists(xml1): os.remove(xml1)
    if os.path.exists(xml2): os.remove(xml2)

    # 3. Generate mipmaps
    for density, size in sizes.items():
        mipmap_dir = os.path.join(res_dir, f"mipmap-{density}")
        os.makedirs(mipmap_dir, exist_ok=True)
        
        resized = img.resize((size, size), Image.Resampling.LANCZOS)
        resized.save(os.path.join(mipmap_dir, "ic_launcher.png"), "PNG")
        
        # create circular mask for ic_launcher_round.png
        mask = Image.new("L", (size, size), 0)
        draw = ImageDraw.Draw(mask)
        draw.ellipse((0, 0, size, size), fill=255)
        
        round_img = Image.new("RGBA", (size, size))
        round_img.paste(resized, (0, 0), mask)
        round_img.save(os.path.join(mipmap_dir, "ic_launcher_round.png"), "PNG")

print("Icon updated successfully.")
