import sys
import os

files = [
    r"d:\Project\KonaBess-Next\app\src\main\java\com\ireddragonicy\konabessnext\core\GpuTableEditor.java",
    r"d:\Project\KonaBess-Next\app\src\main\java\com\ireddragonicy\konabessnext\core\TableIO.java",
    r"d:\Project\KonaBess-Next\app\src\main\java\com\ireddragonicy\konabessnext\data\KonaBessStr.java"
]

for f in files:
    try:
        with open(f, 'r', encoding='utf-8') as file:
            content = file.read()
        
        # Replace .type with .Type
        new_content = content.replace("ChipInfo.type.", "ChipInfo.Type.")
        # Also handle cases where it might be just .type if imported static? 
        # But mostly likely fully qualified or class qualified.
        
        if content != new_content:
            with open(f, 'w', encoding='utf-8') as file:
                file.write(new_content)
            print(f"Updated {f}")
        else:
            print(f"No changes in {f}")
    except Exception as e:
        print(f"Error processing {f}: {e}")
