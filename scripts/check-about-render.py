import pathlib
import re
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[1]
CACHE = ROOT / ".gradle-user-home/caches"
SCREEN = "net.minecraft.client.gui.screens.Screen"
SOURCE = "src/main/kotlin/dev/spotlight/ui/AboutScreen.kt"


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


for version, worktrees in {
    "26.3": ["."],
    "26.2": ["build/backports/26.2"],
    "26.1": ["build/backports/26.1"],
    "1.21.11": ["build/backports/1.21.11"],
    "1.21.4": ["build/preview-fixes/1.21.4"],
    "1.21.1": ["build/preview-fixes/main", "build/preview-fixes/1.21.1"],
    "1.20.1": ["build/preview-fixes/1.20.1"],
}.items():
    jars = [
        p for p in (CACHE / "fabric-loom/minecraftMaven/net/minecraft").rglob("*.jar")
        if "intermediary" not in str(p)
        and (f"/{version}-" in str(p) or f"/{version}/" in str(p))
    ]
    assert len(jars) == 1, (version, jars)
    native = methods(jars[0])
    render = "extractRenderState" if version.startswith("26.") else "render"
    background = "extractBackground" if version.startswith("26.") else "renderBackground"
    late_background = f"Method {background}:" in native[render]
    for worktree in worktrees:
        audit(ROOT / worktree, render, background, late_background, jars[0])
        source = (ROOT / worktree / SOURCE).read_text()
        if late_background:
            assert f"override fun {render}(" not in source, worktree
            assert source.index(f"super.{background}(") < source.index("g.fill("), worktree
            assert f"override fun {background}(" in source, worktree
        else:
            assert f"Method {background}:" not in native[render], version
            assert source.index("g.fill(left,") < source.index(f"super.{render}("), worktree
            if version != "1.20.1":
                wrapper = native[next(k for k in native if k.endswith("WithTooltipAndSubtitles"))]
                assert wrapper.index(f"Method {background}:") < wrapper.index(f"Method {render}:")
                assert "nextStratum" in wrapper
                blur = native["extractBlurredBackground" if version.startswith("26.") else "renderBlurredBackground"]
                assert "getMenuBackgroundBlurriness" in blur and "blurBeforeThisStratum" in blur
        print(f"PASS {worktree}: native background before About content before widgets")

neo = CACHE / "neoformruntime/intermediate_results/sourcesAndCompiledWithNeoForge_c4b5d68e4e3827477a0359123b246ea5f545de04_output.jar"
native = methods(neo)
audit(ROOT / "build/backports/neoforge-1.21.1", "render", "renderBackground", True, neo)
assert native["render"].index("Method renderBackground:") < native["render"].index("Field renderables:")
source = (ROOT / "build/backports/neoforge-1.21.1" / SOURCE).read_text()
assert "override fun render(" not in source
assert source.index("super.renderBackground(") < source.index("g.fill(")
assert "processBlurEffect" in native["renderBlurredBackground"]
print("PASS NeoForge 1.21.1: native blur before About content before widgets")

forge = CACHE / "neoformruntime/intermediate_results/binaryPatch_22cd13ae7515335c246992dc9718c1c329cda349_output.jar"
audit(ROOT / "build/backports/forge-1.20.1", "render", "renderBackground", False, forge)
native = methods(forge)["m_88315_"]
assert "invokevirtual" not in native and "Renderable.m_88315_" in native
source = (ROOT / "build/backports/forge-1.20.1" / SOURCE).read_text()
assert source.index("g.fill(left,") < source.index("super.render(")
print("PASS Forge 1.20.1: native render draws widgets only")
