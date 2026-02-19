
try:
    with open("build_log.txt", "r", encoding="utf-8", errors="ignore") as f:
        lines = f.readlines()
        
    print("Total lines:", len(lines))
    for line in lines[-50:]:
        print(line.strip())

except Exception as e:
    print(f"Error reading log: {e}")
