"""Evaluate extraction regex chains without running blob extraction or builds."""
import ast
from pathlib import Path
import re

REPO = Path(__file__).resolve().parents[1]

def fixup(path, text):
    tree = ast.parse((REPO / "extract-files.py").read_text())
    mapping = next(node.value for node in tree.body
                   if isinstance(node, ast.AnnAssign)
                   and isinstance(node.target, ast.Name)
                   and node.target.id == "blob_fixups")
    expression = next(value for key, value in zip(mapping.keys, mapping.values)
                      if ast.literal_eval(key) == path)
    calls = []
    while isinstance(expression, ast.Call) and isinstance(expression.func, ast.Attribute):
        if expression.func.attr != "regex_replace":
            raise AssertionError("Unexpected fixup operation: " + expression.func.attr)
        calls.append(tuple(ast.literal_eval(arg) for arg in expression.args))
        expression = expression.func.value
    for pattern, replacement in reversed(calls):
        text = re.sub(pattern, replacement, text)
    return text
