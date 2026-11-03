package dev.spotlight.validation;

import java.lang.reflect.InvocationTargetException;

public final class NeoForgeCheckLauncher {
    public static void main(String[] args) throws Throwable {
        if (args.length < 1) throw new IllegalArgumentException("Expected one assertion entrypoint");
        var loader = (ClassLoader) Class.forName("net.neoforged.fml.junit.LaunchWrapper")
                .getMethod("getTransformingLoader").invoke(null);
        Thread.currentThread().setContextClassLoader(loader);
        loader.setDefaultAssertionStatus(true);
        try {
            Class.forName(args[0], true, loader).getMethod("main", String[].class)
                    .invoke(null, (Object) java.util.Arrays.copyOfRange(args, 1, args.length));
            System.out.println("NeoForge assertion entrypoint passed: " + args[0]);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
