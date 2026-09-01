package ch.unige.biochem.scijava.introspect;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.scijava.Context;
import org.scijava.command.Command;

import org.scijava.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * CLI entry point for scijava-introspect.
 *
 * Usage: java -cp &lt;classpath&gt; ch.unige.biochem.scijava.introspect.CLI &lt;subcommand&gt; &lt;args...&gt;
 *
 * Subcommands:
 *   list-commands &lt;package1&gt; [package2 ...]
 *   describe-command &lt;className1&gt; [className2 ...]
 *   source-code &lt;className1&gt; [className2 ...]
 *   snapshot &lt;package1&gt; [package2 ...]
 *   diff &lt;old-snapshot.json&gt; &lt;new-snapshot.json&gt;
 *   tree &lt;package1&gt; [package2 ...]
 */
public class CLI {

    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) {
        // Guarantee that stdout carries only the machine-readable payload. Third-party
        // libraries used during run() (e.g. Reflections via a logback SLF4J binding) may
        // log to System.out depending on what backend is on the classpath, which would
        // corrupt the JSON/text payload. Redirect System.out to System.err for the
        // duration of run() so any such logging is preserved as a diagnostic on stderr
        // instead, then restore the real stdout and write only the payload to it.
        java.io.PrintStream realOut = System.out;
        System.setOut(System.err);
        Result result;
        try {
            result = run(args);
        } finally {
            System.setOut(realOut);
        }
        if (result.stderr != null) {
            System.err.print(result.stderr);
        }
        if (result.stdout != null) {
            realOut.println(result.stdout);
        }
        System.exit(result.exitCode);
    }

    /**
     * Runs the CLI logic and returns a Result, without calling System.exit or
     * printing to stdout/stderr. This makes the CLI testable.
     */
    static Result run(String[] args) {
        if (args.length < 1) {
            return Result.error(1, usageMessage());
        }

        String subcommand = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        if (rest.length == 0) {
            return Result.error(1,
                    "Error: subcommand '" + subcommand + "' requires at least one argument.\n" + usageMessage());
        }

        switch (subcommand) {
            case "list-commands":
                return listCommands(rest);
            case "describe-command":
                return describeCommand(rest);
            case "source-code":
                return sourceCode(rest);
            case "snapshot":
                return snapshot(rest);
            case "diff":
                return diff(rest);
            case "tree":
                return tree(rest);
            default:
                return Result.error(1,
                        "Error: unknown subcommand '" + subcommand + "'.\n" + usageMessage());
        }
    }

    private static Result listCommands(String[] packages) {
        JsonArray result = new JsonArray();
        for (String pkg : packages) {
            List<Class<? extends Command>> commands = CommandInvestigator.getCommandsFromPackage(pkg);
            for (Class<? extends Command> cmd : commands) {
                result.add(cmd.getName());
            }
        }
        return Result.success(GSON.toJson(result));
    }

    @SuppressWarnings("unchecked")
    private static Result describeCommand(String[] classNames) {
        JsonArray result = new JsonArray();
        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                if (!Command.class.isAssignableFrom(clazz)) {
                    JsonObject error = new JsonObject();
                    error.addProperty("name", className);
                    error.addProperty("error", "Class does not implement Command");
                    result.add(error);
                    continue;
                }
                String json = CommandInvestigator.toJson((Class<? extends Command>) clazz);
                JsonArray parsed = JsonParser.parseString(json).getAsJsonArray();
                for (int i = 0; i < parsed.size(); i++) {
                    result.add(parsed.get(i));
                }
            } catch (ClassNotFoundException e) {
                JsonObject error = new JsonObject();
                error.addProperty("name", className);
                error.addProperty("error", "Class not found: " + e.getMessage());
                result.add(error);
            } catch (LinkageError e) {
                JsonObject error = new JsonObject();
                error.addProperty("name", className);
                error.addProperty("error", unresolvableMessage(e));
                result.add(error);
            }
        }
        return Result.success(GSON.toJson(result));
    }

    /**
     * The class itself was found, but loading it or reading its fields needs a type that is
     * not on the classpath. This is the common shape of a missing plugin dependency, so it
     * is reported per class rather than being allowed to abort the whole run.
     */
    private static String unresolvableMessage(LinkageError e) {
        return "Class could not be resolved, usually because a dependency is missing from the classpath: "
                + e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    private static Result sourceCode(String[] classNames) {
        Context context = new Context();
        try {
            JsonObject result = new JsonObject();
            for (String className : classNames) {
                try {
                    Class<?> clazz = Class.forName(className);
                    String source = CommandInvestigator.getSourceCode(clazz, context);
                    result.addProperty(className, source);
                } catch (ClassNotFoundException e) {
                    result.addProperty(className, "Error: Class not found: " + e.getMessage());
                } catch (LinkageError e) {
                    result.addProperty(className, "Error: " + unresolvableMessage(e));
                } catch (RuntimeException e) {
                    result.addProperty(className, "Error: " + e.getMessage());
                }
            }
            return Result.success(GSON.toJson(result));

        } finally {
            context.dispose();
        }
    }

    /**
     * Discovers all commands in the given packages and describes each one.
     * Output is a JSON object keyed by fully qualified class name, where each
     * value is the command description (inputs, outputs, etc.).
     */
    @SuppressWarnings("unchecked")
    private static Result snapshot(String[] packages) {
        JsonObject result = new JsonObject();
        for (String pkg : packages) {
            List<Class<? extends Command>> commands = CommandInvestigator.getCommandsFromPackage(pkg);
            for (Class<? extends Command> cmd : commands) {
                try {
                    String json = CommandInvestigator.toJson(cmd);
                    JsonArray parsed = JsonParser.parseString(json).getAsJsonArray();
                    if (parsed.size() > 0) {
                        result.add(cmd.getName(), parsed.get(0));
                    }
                } catch (LinkageError e) {
                    JsonObject error = new JsonObject();
                    error.addProperty("name", cmd.getName());
                    error.addProperty("error", unresolvableMessage(e));
                    result.add(cmd.getName(), error);
                }
            }
        }
        return Result.success(GSON.toJson(result));
    }

    /**
     * Compares two snapshot JSON files and reports added, removed, and modified commands.
     * Expects exactly two arguments: paths to old and new snapshot files.
     */
    private static Result diff(String[] args) {
        if (args.length != 2) {
            return Result.error(1, "Error: 'diff' requires exactly 2 arguments: <old-snapshot.json> <new-snapshot.json>\n");
        }

        String oldContent;
        String newContent;
        try {
            oldContent = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return Result.error(1, "Error reading old snapshot: " + e.getMessage() + "\n");
        }
        try {
            newContent = new String(Files.readAllBytes(Paths.get(args[1])), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return Result.error(1, "Error reading new snapshot: " + e.getMessage() + "\n");
        }

        return diffSnapshots(oldContent, newContent);
    }

    /**
     * Compares two snapshot JSON strings and reports added, removed, and modified commands.
     * Package-private for testability.
     */
    static Result diffSnapshots(String oldContent, String newContent) {
        JsonObject oldSnapshot = JsonParser.parseString(oldContent).getAsJsonObject();
        JsonObject newSnapshot = JsonParser.parseString(newContent).getAsJsonObject();

        TreeSet<String> oldKeys = new TreeSet<>();
        for (Map.Entry<String, JsonElement> entry : oldSnapshot.entrySet()) {
            oldKeys.add(entry.getKey());
        }
        TreeSet<String> newKeys = new TreeSet<>();
        for (Map.Entry<String, JsonElement> entry : newSnapshot.entrySet()) {
            newKeys.add(entry.getKey());
        }

        JsonArray added = new JsonArray();
        JsonArray removed = new JsonArray();
        JsonArray modified = new JsonArray();
        JsonArray unchanged = new JsonArray();

        // Commands only in new snapshot
        for (String key : newKeys) {
            if (!oldKeys.contains(key)) {
                added.add(key);
            }
        }

        // Commands only in old snapshot
        for (String key : oldKeys) {
            if (!newKeys.contains(key)) {
                removed.add(key);
            }
        }

        // Commands in both — compare JSON descriptions
        for (String key : oldKeys) {
            if (newKeys.contains(key)) {
                JsonElement oldDesc = oldSnapshot.get(key);
                JsonElement newDesc = newSnapshot.get(key);
                if (oldDesc.equals(newDesc)) {
                    unchanged.add(key);
                } else {
                    modified.add(key);
                }
            }
        }

        JsonObject result = new JsonObject();
        result.add("added", added);
        result.add("removed", removed);
        result.add("modified", modified);
        result.add("unchanged", unchanged);

        return Result.success(GSON.toJson(result));
    }

    /**
     * Builds human-readable trees from commands in the given packages:
     * first a menu hierarchy (from @Plugin menuPath or menu), then a package hierarchy.
     */
    private static Result tree(String[] packages) {
        List<String[]> entries = new ArrayList<>(); // each: [menuPath, className]
        for (String pkg : packages) {
            List<Class<? extends Command>> commands = CommandInvestigator.getCommandsFromPackage(pkg);
            for (Class<? extends Command> cmd : commands) {
                String menuPath;
                try {
                    Plugin plugin = cmd.getAnnotation(Plugin.class);
                    menuPath = CommandInvestigator.resolveMenuPath(plugin);
                } catch (LinkageError e) {
                    // Keep the command in the tree; only its menu placement is unknown
                    menuPath = null;
                }
                entries.add(new String[]{menuPath, cmd.getName()});
            }
        }
        return buildTree(entries);
    }

    /**
     * Builds both menu and package trees from a list of [menuPath, className] pairs.
     * Package-private for testability.
     */
    static Result buildTree(List<String[]> entries) {
        StringBuilder sb = new StringBuilder();

        // --- Menu hierarchy ---
        sb.append("=== Menu Hierarchy ===\n\n");
        TreeMap<String, Object> menuRoot = new TreeMap<>();
        List<String[]> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator
                .<String[], String>comparing(e -> e[0] == null ? "\uffff" : e[0])
                .thenComparing(e -> e[1]));

        for (String[] entry : sorted) {
            String menuPath = entry[0];
            String className = entry[1];
            String simpleName = className.substring(className.lastIndexOf('.') + 1);

            if (menuPath == null) {
                menuPath = "(no menu path)>" + simpleName;
            }

            // Build full path: menu segments, with leaf = "Menu Label  [ClassName]"
            String[] parts = menuPath.split(">");
            String[] fullPath = Arrays.copyOf(parts, parts.length);
            fullPath[fullPath.length - 1] = parts[parts.length - 1].trim() + "  [" + simpleName + "]";
            insertIntoTree(menuRoot, fullPath);
        }
        renderTree(sb, menuRoot, "");

        // --- Package hierarchy ---
        sb.append("\n=== Package Hierarchy ===\n\n");
        TreeMap<String, Object> pkgRoot = new TreeMap<>();
        List<String[]> bySorted = new ArrayList<>(entries);
        bySorted.sort(Comparator.comparing(e -> e[1]));

        for (String[] entry : bySorted) {
            String className = entry[1];
            String menuPath = entry[0];
            String simpleName = className.substring(className.lastIndexOf('.') + 1);

            // Build full path: all FQN segments, with leaf = "ClassName  (menu: ...)"
            String[] parts = className.split("\\.");
            String leafLabel = simpleName;
            if (menuPath != null) {
                String[] menuParts = menuPath.split(">");
                String menuLeaf = menuParts[menuParts.length - 1].trim();
                leafLabel = simpleName + "  (menu: " + menuLeaf + ")";
            }
            parts[parts.length - 1] = leafLabel;
            insertIntoTree(pkgRoot, parts);
        }
        renderTree(sb, pkgRoot, "");

        return Result.success(sb.toString());
    }

    @SuppressWarnings("unchecked")
    private static void insertIntoTree(TreeMap<String, Object> root, String[] fullPath) {
        TreeMap<String, Object> current = root;
        for (int i = 0; i < fullPath.length - 1; i++) {
            String part = fullPath[i].trim();
            current = (TreeMap<String, Object>) current.computeIfAbsent(part, k -> new TreeMap<String, Object>());
        }
        current.put(fullPath[fullPath.length - 1], null);
    }

    @SuppressWarnings("unchecked")
    private static void renderTree(StringBuilder sb, TreeMap<String, Object> node, String prefix) {
        List<Map.Entry<String, Object>> entries = new ArrayList<>(node.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Object> entry = entries.get(i);
            boolean isLast = (i == entries.size() - 1);
            String connector = isLast ? "└── " : "├── ";
            sb.append(prefix).append(connector).append(entry.getKey()).append("\n");
            if (entry.getValue() instanceof TreeMap) {
                String childPrefix = prefix + (isLast ? "    " : "│   ");
                renderTree(sb, (TreeMap<String, Object>) entry.getValue(), childPrefix);
            }
        }
    }

    private static String usageMessage() {
        return "Usage: ch.unige.biochem.scijava.introspect.CLI <subcommand> <args...>\n"
                + "\n"
                + "Subcommands:\n"
                + "  list-commands <package1> [package2 ...]         List command class names in packages\n"
                + "  describe-command <className1> [className2 ...]  Describe commands as JSON\n"
                + "  source-code <className1> [className2 ...]       Fetch source code for classes\n"
                + "  snapshot <package1> [package2 ...]              Snapshot all commands in packages\n"
                + "  diff <old-snapshot.json> <new-snapshot.json>    Diff two snapshots\n"
                + "  tree <package1> [package2 ...]                  Menu and package hierarchy trees\n";
    }

    /**
     * Holds the result of a CLI invocation: stdout content, stderr content, and exit code.
     */
    static class Result {
        final String stdout;
        final String stderr;
        final int exitCode;

        Result(String stdout, String stderr, int exitCode) {
            this.stdout = stdout;
            this.stderr = stderr;
            this.exitCode = exitCode;
        }

        static Result success(String stdout) {
            return new Result(stdout, null, 0);
        }

        static Result error(int exitCode, String stderr) {
            return new Result(null, stderr, exitCode);
        }
    }
}