import os

def replace_in_file(filepath):
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
        
        if 'Gravity Installer' in content:
            new_content = content.replace('Gravity Installer', 'Gravity Installer')
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(new_content)
            print(f"Updated: {filepath}")
    except Exception as e:
        print(f"Error processing {filepath}: {e}")

def main():
    skip_dirs = {'.git', '.gradle', 'build', '.idea'}
    skip_exts = {'.png', '.jpg', '.jpeg', '.webp', '.ico', '.jar', '.apk', '.class'}
    
    for root, dirs, files in os.walk('.'):
        dirs[:] = [d for d in dirs if d not in skip_dirs]
        for file in files:
            ext = os.path.splitext(file)[1].lower()
            if ext in skip_exts:
                continue
            
            filepath = os.path.join(root, file)
            replace_in_file(filepath)

if __name__ == '__main__':
    main()
