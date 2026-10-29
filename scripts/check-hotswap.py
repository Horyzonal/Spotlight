#!/usr/bin/env python3
"""Bounded JDI smoke check; requires free loopback port 5005 and JDK 25."""
import os
from pathlib import Path
import subprocess
import tempfile
import time

root = Path(__file__).resolve().parent.parent
home = Path(os.environ.get("JAVA_HOME", str(Path.home() / "Library/Application Support/PrismLauncher/java/java-runtime-epsilon/jre.bundle/Contents/Home")))
java, javac = str(home / "bin/java"), str(home / "bin/javac")
with tempfile.TemporaryDirectory(dir=root / "build") as directory:
    work = Path(directory)
    package = work / "dev/spotlight"
    package.mkdir(parents=True)
    source = package / "Probe.java"
    template = '''package dev.spotlight;
public class Probe {
    EXTRA
    static int value() { return VALUE; }
    public static void main(String[] args) throws Exception {
        while (true) { System.out.println("value=" + value()); Thread.sleep(100); }
    }
}
'''

    def compile_probe(value, extra=""):
        source.write_text(template.replace("VALUE", str(value)).replace("EXTRA", extra))
        subprocess.run([javac, "-d", str(work), str(source)], check=True, timeout=20)

    def swap():
        return subprocess.run([java, "--add-modules", "jdk.jdi", str(root / "scripts/HotSwap.java"), str(work)], capture_output=True, text=True, timeout=20)

    def await_output(text):
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            output = (work / "target.log").read_text()
            if text in output:
                return
            if target.poll() is not None:
                raise AssertionError(output)
            time.sleep(0.1)
        raise AssertionError("Missing " + text + ": " + output)

    compile_probe(1)
    unloaded = package / "Unloaded.java"
    unloaded.write_text("package dev.spotlight; public class Unloaded {}")
    subprocess.run([javac, "-d", str(work), str(unloaded)], check=True, timeout=20)
    with (work / "target.log").open("w") as log:
        target = subprocess.Popen([java, "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:5005", "-cp", str(work), "dev.spotlight.Probe"], stdout=log, stderr=log)
        try:
            await_output("value=1")
            compile_probe(2)
            result = swap()
            assert result.returncode == 0, result.stdout + result.stderr
            assert "Skip not loaded: dev.spotlight.Unloaded" in result.stdout, result.stdout
            await_output("value=2")
            compile_probe(3, "static int newField;")
            result = swap()
            assert result.returncode != 0 and "Restart client for structural changes" in result.stderr, result.stdout + result.stderr
            compile_probe(4)
            result = swap()
            assert result.returncode == 0, result.stdout + result.stderr
            await_output("value=4")
            print("HotSwap checks passed: method update, unloaded skip, structural rejection, disconnect/reconnect.")
        finally:
            target.terminate()
            try:
                target.wait(timeout=5)
            except subprocess.TimeoutExpired:
                target.kill()
                target.wait(timeout=5)
