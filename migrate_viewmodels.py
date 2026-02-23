import subprocess

try:
    print("Running gradle build...")
    result = subprocess.run(
        ["./gradlew.bat", "assembleDebug", "--console=plain"],
        capture_output=True,
        text=True,
        cwd=r"c:\Users\crist\AndroidStudioProjects\Banana - copia"
    )
    lines = (result.stdout + "\n" + result.stderr).splitlines()
    error_lines = []
    capture = False
    
    for line in lines:
        if "FAILED" in line or "[Hilt]" in line or "e:" in line or "error" in line.lower():
            capture = True
        
        if capture:
            error_lines.append(line)
            
    print("--- ERRORS FOUND ---")
    print("\n".join(error_lines[-100:])) # Print last 100 error contextual lines
except Exception as e:
    print("Python error:", e)
