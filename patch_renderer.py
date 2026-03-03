import re

with open('frontend/src/renderer.js', 'r', encoding='utf-8') as f:
    js = f.read()

# 1. Add delete button
pattern1 = r'rw\.appendChild\(clear\);(\s*)rw\.appendChild\(preview\);'
repl1 = r'''rw.appendChild(clear);
    const delBtn = document.createElement("button");
    delBtn.type = "button";
    delBtn.className = "btn btn-mini btn-danger choice-delete-btn";
    delBtn.textContent = "✖";
    delBtn.onclick = () => { block.choices.splice(i, 1); onUpdate(block); };
    rw.appendChild(delBtn);
    \1rw.appendChild(preview);'''

js = re.sub(pattern1, repl1, js)

# 2. Answers input
# Check if answer logic exists or if it's missing.
# We will inject an answer editor at the end of the question block rendering, if it doesn't exist.

with open('frontend/src/renderer.js', 'w', encoding='utf-8') as f:
    f.write(js)

