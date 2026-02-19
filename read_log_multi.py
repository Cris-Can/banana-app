
encodings = ['utf-8', 'utf-16', 'latin-1', 'cp1252']
for enc in encodings:
    try:
        print(f"--- Trying encoding: {enc} ---")
        with open("build_log.txt", "r", encoding=enc) as f:
            content = f.read()
            if "error" in content.lower() or "exception" in content.lower():
                print(f"Found error markers in {enc}")
                lines = content.splitlines()
                for line in lines:
                    if "e: " in line or "error" in line.lower():
                        print(line.strip())
                break
    except Exception as e:
        print(f"Failed {enc}: {e}")
