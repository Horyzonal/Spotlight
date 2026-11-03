package dev.spotlight.check;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModWorkManager;

public final class LoaderCheck {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ModLoader.get().gatherAndInitializeMods(ModWorkManager.syncExecutor(),
                ModWorkManager.parallelExecutor(), () -> {});
        if (!ModList.get().isLoaded("spotlight") || !ModList.get().isLoaded("kotlinforforge")) {
            throw new AssertionError("Native loader did not construct Spotlight and Kotlin For Forge");
        }
        String check = System.getProperty("spotlight.check.main");
        Class.forName(check).getMethod("main", String[].class).invoke(null, (Object) (Boolean.getBoolean("spotlight.check.liveHeads") ? new String[]{"--live"} : new String[0]));
        System.out.println("Loader check passed: " + check);
        System.exit(0);
    }
}
