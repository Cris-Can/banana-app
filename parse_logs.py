import sys

with open('build_log2.txt', 'r', encoding='utf-8', errors='replace') as infile:
    lines = infile.readlines()

errors = []
for i, line in enumerate(lines):
    if '[Hilt]' in line or '[ksp]' in line.lower() or 'e: ' in line.lower():
        # Capturar contexto
        start = max(0, i - 1)
        end = min(len(lines), i + 3)
        errors.append("".join(lines[start:end]))
        
with open('short_errors.txt', 'w', encoding='utf-8') as outfile:
    outfile.write("\n---\n".join(errors[-10:])) # Ultimos 10 errores
