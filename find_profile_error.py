
try:
    with open("build_log.txt", "r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            if "e: file:///" in line and "ProfileScreen.kt" in line:
                print(line.strip())
            elif "error:" in line and "ProfileScreen.kt" in line:
                print(line.strip())
except Exception as e:
    print(f"Error: {e}")
