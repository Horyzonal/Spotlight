import os
import pathlib
import re
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[1]
CACHE = pathlib.Path(os.environ.get("GRADLE_USER_HOME", ROOT / ".gradle-user-home")) / "caches"
SCREEN = "net.minecraft.client.gui.screens.Screen"

def methods(jar, name=SCREEN):
    output = subprocess.check_output(
        ["javap", "-c", "-p", "-classpath", str(jar), name], text=True
    )
    return {
        match.group(1): block
        for block in re.split(r"\n(?=  (?:public|protected|private) )", output)
        if (match := re.search(r"\b(\w+)\([^\n]*\);", block.split("Code:")[0]))
    }


def audit(root, render, background, late_background, jar):
    ui = root / "src/main/kotlin/dev/spotlight/ui"
    count = 0
    for path in sorted(ui.rglob("*.kt")):
        source = path.read_text()
        for match in re.finditer(
            rf"^    override fun {render}\((\w+): GuiGraphics(?:Extractor)?, mouseX: Int, mouseY: Int, delta: Float\) \{{\n(.*?)^    \}}",
            source, re.M | re.S,
        ):
            body = match[2]
            if late_background:
                assert body.lstrip().startswith(f"super.{render}("), path
            assert body.count(f"super.{render}(") == 1, path
            count += 1
        if late_background:
            for match in re.finditer(
                rf"^    override fun {background}\((\w+): GuiGraphics(?:Extractor)?, mouseX: Int, mouseY: Int, delta: Float\) \{{\n(.*?)^    \}}",
                source, re.M | re.S,
            ):
                body = match[2]
                if path.stem not in {"SpotlightScreen", "CommandMenuScreen", "ConfirmSpotlightScreen"}:
                    assert body.lstrip().startswith(f"super.{background}("), path
                    assert body.count(f"super.{background}(") == 1, path
                else:
                    assert f"super.{background}(" not in body, path
                    assert body.lstrip().startswith(f"{match[1]}.fill(0, 0, width, height,"), path
    assert count >= 38, (root, count)
    chest = (ui / "ClientChestScreen.kt").read_text()
    storage = chest.split("abstract class StorageScreen", 1)[1].split("internal fun storageContentContains", 1)[0]
    assert storage.index("enableScissor") < storage.index(f"super.{render}(") < storage.index("disableScissor"), root
    if late_background:
        native = methods(jar)
        assert native[render].index(f"Method {background}:") < native[render].index("Field renderables:"), root
        assert "processBlurEffect" in native["renderBlurredBackground"], root
        assert storage.index(f"super.{background}(") < storage.index("enableScissor"), root
        container = methods(jar, "net.minecraft.client.gui.screens.inventory.AbstractContainerScreen")
        assert "Method renderBg:" in container["renderBackground"], root
        assert "processBlurEffect" not in container["renderBackground"], root
        entry = "screens/Screen.render:" if "screens/Screen.render:" in container["render"] else "Method renderBackground:"
        assert container["render"].index(entry) < container["render"].index("Method renderSlot"), root
    print(f"PASS {root.relative_to(ROOT)}: all UI render overrides audited ({count}); storage scissor and native container preserved")


jar = CACHE / "neoformruntime/intermediate_results/binaryPatch_22cd13ae7515335c246992dc9718c1c329cda349_output.jar"
audit(ROOT, "render", "renderBackground", False, jar)
look = (ROOT / "src/main/kotlin/dev/spotlight/ui/LookScreen.kt").read_text()
assert look.count(": StorageScreen(") == 3
assert "override fun render(" not in look
assert "override fun renderBackground(" not in look
print("PASS Look screens inherit native background ordering and storage clipping")
