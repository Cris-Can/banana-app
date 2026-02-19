
import re

def parse_structure(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    stack = []
    
    for i, line in enumerate(lines):
        line_num = i + 1
        stripped = line.strip()
        
        # Check for opens
        # We look for "Column", "Box", "Row", "Card", "Scaffold", "BananaCard" followed by "{" possibly on same or next lines
        # This is a simple heuristic
        
        # Count { and }
        open_count = line.count('{')
        close_count = line.count('}')
        
        # Attempt to identify component name
        component = None
        if "Column" in line: component = "Column"
        elif "Box" in line: component = "Box"
        elif "Row" in line: component = "Row"
        elif "Card" in line: component = "Card"
        elif "Scaffold" in line: component = "Scaffold"
        elif "BananaCard" in line: component = "BananaCard"
        elif "LazyColumn" in line: component = "LazyColumn"
        
        if open_count > 0:
            for _ in range(open_count):
                stack.append((line_num, component if component else "Block"))
                print(f"L{line_num}: Open {stack[-1][1]} (Depth: {len(stack)})")
                
        if close_count > 0:
            for _ in range(close_count):
                if stack:
                    popped = stack.pop()
                    output_line = f"L{line_num}: Close {popped[1]} started at L{popped[0]} (Depth: {len(stack)})\n"
                else:
                    output_line = f"L{line_num}: ERROR - Extra Closing Brace\n"
                
                if 550 <= line_num <= 650:
                    with open("structure_log_utf8.txt", "a", encoding="utf-8") as out:
                        out.write(output_line)

        if open_count > 0:
            for _ in range(open_count):
                stack.append((line_num, component if component else "Block"))
                output_line = f"L{line_num}: Open {stack[-1][1]} (Depth: {len(stack)})\n"
                if 550 <= line_num <= 650:
                    with open("structure_log_utf8.txt", "a", encoding="utf-8") as out:
                         out.write(output_line)

if __name__ == "__main__":
    with open("structure_log_utf8.txt", "w", encoding="utf-8") as f:
        f.write("Structure Log:\n")
    parse_structure(r"app\src\main\java\com\eventos\banana\ui\profile\ProfileScreen.kt")
