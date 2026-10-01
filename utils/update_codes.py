import json
from sys import argv

# DRS v1.25.0 audit note: authoring helper for single-character layout
# keys — it derives `code` from `label` with ord(). Multi-character
# labels (popups, special keys) have no single code point, so they are
# skipped with a warning instead of crashing on TypeError like before.

if len(argv) != 2:
    print(f"Usage: {argv[0]} FILE")
    exit(1)

with open(argv[1], "r") as file:
    layout_json = json.load(file)

for i, row in enumerate(layout_json["arrangement"]):
    for j, key in enumerate(row):
        ch = key["label"]

        if len(ch) != 1:
            print(f"skip [{i}][{j}] label={ch!r}: not a single character")
            continue

        layout_json["arrangement"][i][j]["code"] = ord(ch)

with open(argv[1], "w") as file:
    json.dump(layout_json, file, ensure_ascii=False)