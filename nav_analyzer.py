import sys

lines = open('app/src/main/java/com/eventos/banana/navigation/AppNavigation.kt', 'r', encoding='utf-8').readlines()

# Very basic parser to find top-level composable(...) calls inside NavHost { }
in_nav_host = False
nested_level = 0
composables = [] # List of tuples: (start_line_idx, end_line_idx, first_line_content)

for i, line in enumerate(lines):
    # Very naive tracking just to find the NavHost block
    pass

# Actually, it's easier to just do it via regex matching the first line of each composable, 
# then tracking braces to find the end.

blocks = {} # mapping (start, end) -> content
current_start = -1
brace_count = 0

for i, line in enumerate(lines):
    if current_start == -1:
        if line.strip().startswith('composable(') or line.strip().startswith('composable ('):
            # This handles `composable("route") {` and `composable(...) {`
            current_start = i
            brace_count = line.count('{') - line.count('}')
    else:
        brace_count += line.count('{') - line.count('}')
        if brace_count == 0:
            blocks[(current_start, i)] = "".join(lines[current_start:i+1])
            current_start = -1

print(f"Found {len(blocks)} composables")

for k, content in blocks.items():
    route_name = content.split('"')[1] if '"' in content else "unknown"
    print(f"[{k[0]}-{k[1]}] {route_name}")
