
try:
    with open("build_log_cleanup_final.txt", "r", encoding="utf-8") as f:
        for line in f:
            if "e: file:///" in line:
                # Extract path
                parts = line.split("e: file:///")
                if len(parts) > 1:
                    path_part = parts[1]
                    # Path ends at first colon usually, but Windows paths invoke colons.
                    # The format is e: file:///C:/.../File.kt:Line:Col Error
                    # So look for .kt:
                    if ".kt" in path_part:
                        filename = path_part.split(".kt")[0] + ".kt"
                        print(filename)
except Exception as e:
    print(f"Error: {e}")
