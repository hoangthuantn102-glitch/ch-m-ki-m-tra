with open('/app/app/src/main/java/com/example/ui/MainLayout.kt', 'r', encoding='utf-8', errors='ignore') as f:
    lines = f.readlines()

new_lines = []
i = 0
while i < len(lines):
    line = lines[i]
    if 'Text("Xóa Bài Này"' in line:
        new_lines.append(line)
        i += 1
        while i < len(lines):
            l = lines[i]
            if '}' in l:
                i += 1
                break
            i += 1
        new_lines.append('                        }\n')
        new_lines.append('                    }\n')
        while i < len(lines) and 'Spacer' not in lines[i] and 'Row' not in lines[i] and 'Card' not in lines[i] and 'item {' not in lines[i]:
            i += 1
        continue
    new_lines.append(line)
    i += 1

with open('/app/app/src/main/java/com/example/ui/MainLayout.kt', 'w', encoding='utf-8') as f:
    f.writelines(new_lines)
print("Fix script completed successfully.")
