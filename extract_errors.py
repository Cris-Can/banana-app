import re
with open('final_error.txt', 'r', encoding='utf-8', errors='ignore') as f:
    text = f.read()

errors = re.findall(r'e: file:.*', text)
with open('kotlin_errors.txt', 'w', encoding='utf-8') as out:
    for e in errors:
        out.write(e + '\n')
