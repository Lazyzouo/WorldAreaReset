import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public final class ValidateYaml {

    private static final List<String> EXCLUDED_DIRECTORIES = List.of(".git", ".gradle", "build");

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        AtomicInteger count = new AtomicInteger();

        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                if (isExcluded(root, path) || !isYaml(path)) {
                    continue;
                }

                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    yaml.load(reader);
                    count.incrementAndGet();
                } catch (Exception error) {
                    throw new IllegalStateException("Invalid YAML: " + path + " (" + error.getMessage() + ")", error);
                }
            }
        }

        System.out.println("Validated " + count.get() + " YAML files.");
    }

    private static boolean isExcluded(Path root, Path path) {
        Path relative = root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize());
        for (Path part : relative) {
            if (EXCLUDED_DIRECTORIES.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isYaml(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }
}
