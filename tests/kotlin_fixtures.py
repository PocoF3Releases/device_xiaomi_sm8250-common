"""Compile real Kotlin owners against simulated Android boundaries, exclusively in /tmp."""
import os
from pathlib import Path
import subprocess


def execute(root, jdk, repo, source_names, main):
    checkout = repo.parents[2]
    kotlin = checkout / "external/kotlinc"
    stdlib = kotlin / "lib/kotlin-stdlib.jar"
    classes = root / "classes"
    tests = [p for p in root.rglob("Test.java")]
    boundaries = [p for p in root.rglob("*.java") if p not in tests]
    subprocess.run([str(jdk / "bin/javac"), "-d", str(classes), *map(str, boundaries)], check=True)
    env = dict(os.environ, JAVA_HOME=str(jdk))
    subprocess.run([str(kotlin / "bin/kotlinc"), "-jvm-target", "17", "-classpath", str(classes),
                    "-d", str(classes), *[str(repo / "parts/src/org/lineageos/settings" / name) for name in source_names]], check=True, env=env)
    cp = str(classes) + ":" + str(stdlib)
    subprocess.run([str(jdk / "bin/javac"), "-cp", cp, "-d", str(classes), *map(str, tests)], check=True)
    return subprocess.run([str(jdk / "bin/java"), "-cp", cp, main], capture_output=True, text=True)
