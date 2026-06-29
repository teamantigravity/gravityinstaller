import os
import re

# 1. Update strings.xml (ID and English text)
res_dir = r"c:\Users\varma\OneDrive\Desktop\Projects\gravityinstaller\app\src\main\res"
for root, _, files in os.walk(res_dir):
    for file in files:
        if file == "strings.xml":
            path = os.path.join(root, file)
            with open(path, "r", encoding="utf-8") as f:
                content = f.read()
            if "theme_preset_orange" in content:
                content = content.replace("theme_preset_orange", "theme_preset_default_blue")
                # Fix the default english one
                if "values\\strings.xml" in path or "values/strings.xml" in path.replace('\\', '/'):
                    content = content.replace("Orange (Default)", "Default Blue")
                with open(path, "w", encoding="utf-8") as f:
                    f.write(content)

# 2. Update ThemeScreen.kt
theme_screen = r"c:\Users\varma\OneDrive\Desktop\Projects\gravityinstaller\app\src\main\java\app\pwhs\universalinstaller\presentation\setting\theme\ThemeScreen.kt"
with open(theme_screen, "r", encoding="utf-8") as f:
    content = f.read()

content = content.replace("theme_preset_orange", "theme_preset_default_blue")
content = content.replace("Color(0xFFEA580C)", "Color(0xFF1A73E8)")

with open(theme_screen, "w", encoding="utf-8") as f:
    f.write(content)

# 3. Update libs.versions.toml
toml_path = r"c:\Users\varma\OneDrive\Desktop\Projects\gravityinstaller\gradle\libs.versions.toml"
with open(toml_path, "r", encoding="utf-8") as f:
    content = f.read()

if "guava =" not in content:
    content = content.replace('[versions]\n', '[versions]\nguava = "32.1.2-android"\n')
    content = content.replace('[libraries]\n', '[libraries]\nguava = { module = "com.google.guava:guava", version.ref = "guava" }\n')

with open(toml_path, "w", encoding="utf-8") as f:
    f.write(content)

# 4. Update app/build.gradle.kts
gradle_path = r"c:\Users\varma\OneDrive\Desktop\Projects\gravityinstaller\app\build.gradle.kts"
with open(gradle_path, "r", encoding="utf-8") as f:
    content = f.read()

if "libs.guava" not in content:
    content = content.replace("dependencies {\n", "dependencies {\n    implementation(libs.guava)\n")

with open(gradle_path, "w", encoding="utf-8") as f:
    f.write(content)

print("Done")
