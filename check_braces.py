
import sys

def check_braces(filepath):
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        stack = []
        for i, line in enumerate(lines):
            for j, char in enumerate(line):
                if char == '{':
                    stack.append((i + 1, j + 1))
                elif char == '}':
                    if not stack:
                        print(f"Error: Unmatched '}}' at line {i + 1}, column {j + 1}")
                        return
                    stack.pop()
        
        if stack:
            print(f"Error: Unmatched '{{' at line {stack[-1][0]}, column {stack[-1][1]}")
        else:
            print("Braces are balanced.")
            
    except Exception as e:
        print(f"Error: {e}")

check_braces(r"app\src\main\java\com\eventos\banana\ui\profile\ProfileScreen.kt")
