package dev.spotlight.launcher;

import net.minecraftforge.fml.loading.targets.ForgeClientUserdevLaunchHandler;

public final class CheckLaunchHandler extends ForgeClientUserdevLaunchHandler {
    @Override
    public String name() { return "spotlightclientchecks"; }

    @Override
    public boolean isData() { return true; }

    public void runService(String[] args, ModuleLayer layer) throws Throwable {
        devService(args, layer);
    }

    public void devService(String[] args, ModuleLayer layer) throws Throwable {
        Class<?> target = Class.forName("dev.spotlight.check.LoaderCheck", true,
                layer.findLoader("spotlight"));
        target.getMethod("main", String[].class).invoke(null, (Object) args);
    }
}
