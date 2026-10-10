"""Exercise real profile detail formatting, including literal percent signs."""
import argparse
import json
from pathlib import Path
import re
import tempfile
import xml.etree.ElementTree as ET
from kotlin_fixtures import execute

repo = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--jdk", type=Path, required=True)
args = parser.parse_args()
source = repo / "parts/src/org/lineageos/settings/thermal"
resources = {}
for path in (repo / "parts/res/values").glob("*.xml"):
    for item in ET.parse(path).getroot():
        if item.tag == "string":
            resources[item.get("name")] = "".join(item.itertext()).replace("\\n", "\n").replace("\\'", "'")
refs = set(re.findall(r"R\.(string|drawable)\.(\w+)", "".join((source / name).read_text() for name in ["ThermalProfiles.kt", "ThermalDetails.kt"])))
ids = {ref: n + 1 for n, ref in enumerate(sorted(refs))}
r = "package org.lineageos.settings; public final class R {"
for kind in ["string", "drawable"]:
    r += "public static final class " + kind + " {" + "".join("public static final int " + name + "=" + str(value) + ";" for (category, name), value in ids.items() if category == kind) + "}"
r += "}"
cases = "".join("case " + str(value) + ": return " + json.dumps(resources[name], ensure_ascii=True) + ";" for (kind, name), value in ids.items() if kind == "string")
context = "package android.content; public class Context {public String getString(int id) {switch(id){" + cases + "default: throw new IllegalArgumentException();}} public String getString(int id,Object... args){return String.format(java.util.Locale.US,getString(id),args);}}"
test = """import org.lineageos.settings.thermal.*;
public class Test {public static void main(String[] args) {
 ThermalDetails d = new ThermalDetails(new android.content.Context()); int count=0;
 for(int region=0;region<2;region++) for(ThermalProfiles.Profile p:ThermalProfiles.INSTANCE.getProfiles(region)) {
  if(d.cpu(p.getPolicy()).isBlank() || d.gpu(p.getPolicy()).isBlank() || d.controls(p.getPolicy()).isBlank() || d.sensor(p.getPolicy()).isBlank()) throw new AssertionError(); count++;
 }
 System.out.println("PASS: all "+count+" regional profile details, literal percentages and parameterized units");
}}"""
with tempfile.TemporaryDirectory(prefix="parts-details-") as folder:
    root = Path(folder)
    for name, text in {"org/lineageos/settings/R.java": r, "android/content/Context.java": context, "Test.java": test}.items():
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
    result = execute(root, args.jdk, repo, ["thermal/ThermalProfiles.kt", "thermal/ThermalDetails.kt"], "Test")
    print(result.stdout, end="")
    if result.returncode: raise RuntimeError(result.stderr)
