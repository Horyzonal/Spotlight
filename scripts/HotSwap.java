import com.sun.jdi.Bootstrap;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

class HotSwap {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: HotSwap.java <compiled class directory> ...");
        }
        var bytecodes = new LinkedHashMap<String, byte[]>();
        for (String directory : args) {
            Path root = Path.of(directory);
            Path packageRoot = root.resolve("dev/spotlight");
            if (!Files.isDirectory(packageRoot)) continue;
            try (var files = Files.walk(packageRoot)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".class")).sorted().toList()) {
                    String relative = root.relativize(file).toString();
                    String name = relative.substring(0, relative.length() - 6).replace('/', '.').replace('\\', '.');
                    if (bytecodes.putIfAbsent(name, Files.readAllBytes(file)) != null) {
                        throw new IllegalArgumentException("Duplicate compiled class: " + name);
                    }
                }
            }
        }
        if (bytecodes.isEmpty()) throw new IllegalStateException("No compiled dev.spotlight classes found.");
        AttachingConnector connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
                .filter(c -> c.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
        var arguments = connector.defaultArguments();
        arguments.get("hostname").setValue("127.0.0.1");
        arguments.get("port").setValue("5005");
        arguments.get("timeout").setValue("5000");
        VirtualMachine vm;
        try {
            vm = connector.attach(arguments);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot attach to 127.0.0.1:5005. Start scripts/dev; disconnect other debuggers.", e);
        }
        try {
            if (!vm.canRedefineClasses()) throw new UnsupportedOperationException("Target JVM cannot redefine classes; restart required.");
            var definitions = new LinkedHashMap<ReferenceType, byte[]>();
            int skipped = 0;
            for (var entry : bytecodes.entrySet()) {
                var loaded = vm.classesByName(entry.getKey());
                if (loaded.isEmpty()) {
                    System.out.println("Skip not loaded: " + entry.getKey());
                    skipped++;
                }
                for (var type : loaded) definitions.put(type, entry.getValue());
            }
            if (!definitions.isEmpty()) {
                try {
                    vm.redefineClasses(definitions);
                } catch (UnsupportedOperationException | LinkageError e) {
                    throw new IllegalStateException("HotSwap rejected. Standard JVM supports method-body changes only. "
                            + "Restart client for structural changes (fields, methods, hierarchy), mixins or incompatible bytecode.", e);
                }
            }
            System.out.println("Redefined " + definitions.size() + " loaded classes; skipped " + skipped + " not loaded classes.");
        } finally {
            vm.dispose();
        }
    }
}
