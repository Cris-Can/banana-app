
try:
    with open("build_log.txt", "r", encoding="utf-8", errors="ignore") as f:
        lines = f.readlines()
        
    errors = []
    capture = False
    for line in lines:
        if "e: file:///" in line or "error:" in line.lower() or "exception" in line.lower() or "failed" in line.lower():
             capture = True
             errors.append(line.strip())
        elif capture and line.strip() and not line.strip().startswith(">"):
             errors.append(line.strip())
        else:
             capture = False
             
    # De-duplicate somewhat
    seen = set()
    unique_errors = []
    for e in errors:
        if e not in seen:
            unique_errors.append(e)
            seen.add(e)
            
    print("\n".join(unique_errors[:50])) # Limit output

except Exception as e:
    print(f"Error reading log: {e}")
