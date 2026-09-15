package ch.unige.biochem.scijava.introspect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Arrays;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

public class CLITest {

    // --- Argument parsing / error handling ---

    @Test
    public void noArgsPrintsUsageAndExitsWithError() {
        CLI.Result result = CLI.run(new String[]{});
        assertEquals(1, result.exitCode);
        assertNull(result.stdout);
        assertTrue(result.stderr.contains("Usage:"));
    }

    @Test
    public void unknownSubcommandExitsWithError() {
        CLI.Result result = CLI.run(new String[]{"bad-command", "foo"});
        assertEquals(1, result.exitCode);
        assertNull(result.stdout);
        assertTrue(result.stderr.contains("unknown subcommand"));
        assertTrue(result.stderr.contains("bad-command"));
    }

    @Test
    public void subcommandWithNoArgsExitsWithError() {
        CLI.Result result = CLI.run(new String[]{"list-commands"});
        assertEquals(1, result.exitCode);
        assertNull(result.stdout);
        assertTrue(result.stderr.contains("requires at least one argument"));
    }

    // --- list-commands ---

    @Test
    public void listCommandsReturnsJsonArray() {
        CLI.Result result = CLI.run(new String[]{"list-commands", "ch.unige.biochem.scijava.introspect"});
        assertEquals(0, result.exitCode);
        assertNull(result.stderr);

        JsonArray arr = JsonParser.parseString(result.stdout).getAsJsonArray();
        assertTrue("Expected at least one command", arr.size() > 0);

        // All entries should be strings starting with the package prefix
        for (JsonElement el : arr) {
            assertTrue(el.isJsonPrimitive());
            assertTrue(el.getAsString().startsWith("ch.unige.biochem.scijava.introspect."));
        }
    }

    @Test
    public void listCommandsEmptyPackageReturnsEmptyArray() {
        CLI.Result result = CLI.run(new String[]{"list-commands", "com.nonexistent.empty.pkg"});
        assertEquals(0, result.exitCode);

        JsonArray arr = JsonParser.parseString(result.stdout).getAsJsonArray();
        assertEquals(0, arr.size());
    }

    @Test
    public void listCommandsMultiplePackagesMergesResults() {
        // Run with two copies of the same package — results should be combined
        CLI.Result single = CLI.run(new String[]{"list-commands", "ch.unige.biochem.scijava.introspect"});
        CLI.Result doubled = CLI.run(new String[]{"list-commands",
                "ch.unige.biochem.scijava.introspect", "ch.unige.biochem.scijava.introspect"});

        JsonArray singleArr = JsonParser.parseString(single.stdout).getAsJsonArray();
        JsonArray doubledArr = JsonParser.parseString(doubled.stdout).getAsJsonArray();
        assertEquals(singleArr.size() * 2, doubledArr.size());
    }

    // --- describe-command ---

    @Test
    public void describeCommandReturnsCommandInfo() {
        CLI.Result result = CLI.run(new String[]{"describe-command",
                "ch.unige.biochem.scijava.introspect.DummySumCommand"});
        assertEquals(0, result.exitCode);

        JsonArray arr = JsonParser.parseString(result.stdout).getAsJsonArray();
        assertEquals(1, arr.size());

        JsonObject cmd = arr.get(0).getAsJsonObject();
        assertEquals("ch.unige.biochem.scijava.introspect.DummySumCommand", cmd.get("name").getAsString());
        assertTrue(cmd.has("input"));
        assertTrue(cmd.has("output"));
    }

    @Test
    public void describeCommandMultipleClassesMergesIntoOneArray() {
        CLI.Result result = CLI.run(new String[]{"describe-command",
                "ch.unige.biochem.scijava.introspect.DummySumCommand",
                "ch.unige.biochem.scijava.introspect.DummySumCommand"});
        assertEquals(0, result.exitCode);

        JsonArray arr = JsonParser.parseString(result.stdout).getAsJsonArray();
        assertEquals(2, arr.size());
    }

    @Test
    public void describeCommandClassNotFoundReturnsErrorInJson() {
        CLI.Result result = CLI.run(new String[]{"describe-command", "com.nonexistent.FakeClass"});
        assertEquals(0, result.exitCode);

        JsonArray arr = JsonParser.parseString(result.stdout).getAsJsonArray();
        assertEquals(1, arr.size());

        JsonObject entry = arr.get(0).getAsJsonObject();
        assertEquals("com.nonexistent.FakeClass", entry.get("name").getAsString());
        assertTrue(entry.get("error").getAsString().contains("Class not found"));
    }

    @Test
    public void describeCommandNonCommandClassReturnsErrorInJson() {
        // String is a valid class but doesn't implement Command
        CLI.Result result = CLI.run(new String[]{"describe-command", "java.lang.String"});
        assertEquals(0, result.exitCode);

        JsonArray arr = JsonParser.parseString(result.stdout).getAsJsonArray();
        assertEquals(1, arr.size());

        JsonObject entry = arr.get(0).getAsJsonObject();
        assertEquals("java.lang.String", entry.get("name").getAsString());
        assertTrue(entry.get("error").getAsString().contains("does not implement Command"));
    }

    @Test
    public void describeCommandMixesValidAndInvalidClasses() {
        CLI.Result result = CLI.run(new String[]{"describe-command",
                "ch.unige.biochem.scijava.introspect.DummySumCommand",
                "com.nonexistent.FakeClass"});
        assertEquals(0, result.exitCode);

        JsonArray arr = JsonParser.parseString(result.stdout).getAsJsonArray();
        assertEquals(2, arr.size());

        // First should be the valid command
        assertTrue(arr.get(0).getAsJsonObject().has("input"));
        // Second should be the error
        assertTrue(arr.get(1).getAsJsonObject().has("error"));
    }

    // --- source-code ---

    @Test
    public void sourceCodeClassNotFoundReturnsErrorInJson() {
        CLI.Result result = CLI.run(new String[]{"source-code", "com.nonexistent.FakeClass"});
        assertEquals(0, result.exitCode);

        JsonObject obj = JsonParser.parseString(result.stdout).getAsJsonObject();
        assertTrue(obj.has("com.nonexistent.FakeClass"));
        assertTrue(obj.get("com.nonexistent.FakeClass").getAsString().contains("Error:"));
    }

    @Test@Ignore
    public void sourceCodeFetchesActualSource() {
        CLI.Result result = CLI.run(new String[]{"source-code",
                "ch.unige.biochem.scijava.introspect.DummySumCommand"});
        assertEquals(0, result.exitCode);

        JsonObject obj = JsonParser.parseString(result.stdout).getAsJsonObject();
        String source = obj.get("ch.unige.biochem.scijava.introspect.DummySumCommand").getAsString();
        // The source should contain the class declaration
        assertTrue(source.contains("public class DummySumCommand"));
        assertFalse(source.startsWith("Error:"));
    }

    // --- snapshot ---

    @Test
    public void snapshotReturnsObjectKeyedByClassName() {
        CLI.Result result = CLI.run(new String[]{"snapshot", "ch.unige.biochem.scijava.introspect"});
        assertEquals(0, result.exitCode);
        assertNull(result.stderr);

        JsonObject obj = JsonParser.parseString(result.stdout).getAsJsonObject();
        assertTrue("Expected at least one command in snapshot", obj.size() > 0);

        // Each key should be a class name, each value should have "name", "input", "output"
        for (String key : obj.keySet()) {
            assertTrue(key.startsWith("ch.unige.biochem.scijava.introspect."));
            JsonObject cmd = obj.get(key).getAsJsonObject();
            assertEquals(key, cmd.get("name").getAsString());
            assertTrue(cmd.has("input"));
            assertTrue(cmd.has("output"));
        }
    }

    @Test
    public void snapshotEmptyPackageReturnsEmptyObject() {
        CLI.Result result = CLI.run(new String[]{"snapshot", "com.nonexistent.empty.pkg"});
        assertEquals(0, result.exitCode);

        JsonObject obj = JsonParser.parseString(result.stdout).getAsJsonObject();
        assertEquals(0, obj.size());
    }

    @Test
    public void snapshotIsDeterministic() {
        // Running snapshot twice should produce identical output
        CLI.Result r1 = CLI.run(new String[]{"snapshot", "ch.unige.biochem.scijava.introspect"});
        CLI.Result r2 = CLI.run(new String[]{"snapshot", "ch.unige.biochem.scijava.introspect"});

        JsonObject o1 = JsonParser.parseString(r1.stdout).getAsJsonObject();
        JsonObject o2 = JsonParser.parseString(r2.stdout).getAsJsonObject();
        assertEquals(o1, o2);
    }

    // --- diff (via diffSnapshots for testability) ---

    @Test
    public void diffIdenticalSnapshotsShowsAllUnchanged() {
        String snapshot = "{\"com.example.CmdA\": {\"name\": \"com.example.CmdA\", \"input\": [], \"output\": []}}";

        CLI.Result result = CLI.diffSnapshots(snapshot, snapshot);
        assertEquals(0, result.exitCode);

        JsonObject diff = JsonParser.parseString(result.stdout).getAsJsonObject();
        assertEquals(0, diff.getAsJsonArray("added").size());
        assertEquals(0, diff.getAsJsonArray("removed").size());
        assertEquals(0, diff.getAsJsonArray("modified").size());
        assertEquals(1, diff.getAsJsonArray("unchanged").size());
        assertEquals("com.example.CmdA", diff.getAsJsonArray("unchanged").get(0).getAsString());
    }

    @Test
    public void diffDetectsAddedCommands() {
        String oldSnapshot = "{}";
        String newSnapshot = "{\"com.example.NewCmd\": {\"name\": \"com.example.NewCmd\", \"input\": [], \"output\": []}}";

        CLI.Result result = CLI.diffSnapshots(oldSnapshot, newSnapshot);
        JsonObject diff = JsonParser.parseString(result.stdout).getAsJsonObject();

        assertEquals(1, diff.getAsJsonArray("added").size());
        assertEquals("com.example.NewCmd", diff.getAsJsonArray("added").get(0).getAsString());
        assertEquals(0, diff.getAsJsonArray("removed").size());
        assertEquals(0, diff.getAsJsonArray("modified").size());
    }

    @Test
    public void diffDetectsRemovedCommands() {
        String oldSnapshot = "{\"com.example.OldCmd\": {\"name\": \"com.example.OldCmd\", \"input\": [], \"output\": []}}";
        String newSnapshot = "{}";

        CLI.Result result = CLI.diffSnapshots(oldSnapshot, newSnapshot);
        JsonObject diff = JsonParser.parseString(result.stdout).getAsJsonObject();

        assertEquals(0, diff.getAsJsonArray("added").size());
        assertEquals(1, diff.getAsJsonArray("removed").size());
        assertEquals("com.example.OldCmd", diff.getAsJsonArray("removed").get(0).getAsString());
    }

    @Test
    public void diffDetectsModifiedCommands() {
        String oldSnapshot = "{\"com.example.Cmd\": {\"name\": \"com.example.Cmd\", \"input\": [{\"type\": \"String\", \"name\": \"foo\"}], \"output\": []}}";
        String newSnapshot = "{\"com.example.Cmd\": {\"name\": \"com.example.Cmd\", \"input\": [{\"type\": \"int\", \"name\": \"foo\"}], \"output\": []}}";

        CLI.Result result = CLI.diffSnapshots(oldSnapshot, newSnapshot);
        JsonObject diff = JsonParser.parseString(result.stdout).getAsJsonObject();

        assertEquals(0, diff.getAsJsonArray("added").size());
        assertEquals(0, diff.getAsJsonArray("removed").size());
        assertEquals(1, diff.getAsJsonArray("modified").size());
        assertEquals("com.example.Cmd", diff.getAsJsonArray("modified").get(0).getAsString());
        assertEquals(0, diff.getAsJsonArray("unchanged").size());
    }

    @Test
    public void diffHandlesMixOfAllCategories() {
        String oldSnapshot = "{"
                + "\"com.example.Unchanged\": {\"name\": \"com.example.Unchanged\", \"input\": [], \"output\": []},"
                + "\"com.example.Modified\": {\"name\": \"com.example.Modified\", \"input\": [{\"type\": \"String\", \"name\": \"a\"}], \"output\": []},"
                + "\"com.example.Removed\": {\"name\": \"com.example.Removed\", \"input\": [], \"output\": []}"
                + "}";
        String newSnapshot = "{"
                + "\"com.example.Unchanged\": {\"name\": \"com.example.Unchanged\", \"input\": [], \"output\": []},"
                + "\"com.example.Modified\": {\"name\": \"com.example.Modified\", \"input\": [{\"type\": \"int\", \"name\": \"a\"}], \"output\": []},"
                + "\"com.example.Added\": {\"name\": \"com.example.Added\", \"input\": [], \"output\": []}"
                + "}";

        CLI.Result result = CLI.diffSnapshots(oldSnapshot, newSnapshot);
        JsonObject diff = JsonParser.parseString(result.stdout).getAsJsonObject();

        assertEquals(1, diff.getAsJsonArray("added").size());
        assertEquals(1, diff.getAsJsonArray("removed").size());
        assertEquals(1, diff.getAsJsonArray("modified").size());
        assertEquals(1, diff.getAsJsonArray("unchanged").size());
    }

    @Test
    public void diffBothEmptyShowsNoChanges() {
        CLI.Result result = CLI.diffSnapshots("{}", "{}");
        JsonObject diff = JsonParser.parseString(result.stdout).getAsJsonObject();

        assertEquals(0, diff.getAsJsonArray("added").size());
        assertEquals(0, diff.getAsJsonArray("removed").size());
        assertEquals(0, diff.getAsJsonArray("modified").size());
        assertEquals(0, diff.getAsJsonArray("unchanged").size());
    }

    @Test
    public void diffSubcommandRequiresTwoArgs() {
        CLI.Result result = CLI.run(new String[]{"diff", "only-one-arg.json"});
        assertEquals(1, result.exitCode);
        assertTrue(result.stderr.contains("requires exactly 2 arguments"));
    }

    @Test
    public void diffSubcommandReportsFileNotFound() {
        CLI.Result result = CLI.run(new String[]{"diff", "nonexistent-old.json", "nonexistent-new.json"});
        assertEquals(1, result.exitCode);
        assertTrue(result.stderr.contains("Error reading old snapshot"));
    }

    // --- tree ---

    @Test
    public void treeReturnsBothHierarchiesAsPlainText() {
        CLI.Result result = CLI.run(new String[]{"tree", "ch.unige.biochem.scijava.introspect"});
        assertEquals(0, result.exitCode);
        assertNull(result.stderr);

        assertTrue(result.stdout.contains("=== Menu Hierarchy ==="));
        assertTrue(result.stdout.contains("=== Package Hierarchy ==="));
        // DummySumCommand is declared with menuPath "Plugins>Sandbox>Dummy Sum"
        assertTrue(result.stdout.contains("Dummy Sum  [DummySumCommand]"));
        assertTrue(result.stdout.contains("DummySumCommand  (menu: Dummy Sum)"));
    }

    @Test
    public void treeNestsMenuSegmentsAndFallsBackWhenMenuPathIsMissing() {
        CLI.Result result = CLI.buildTree(Arrays.asList(
                new String[]{"Plugins>Sandbox>Dummy Sum", "com.example.DummySumCommand"},
                new String[]{null, "com.example.NoMenuCommand"}));

        assertEquals(0, result.exitCode);
        assertTrue(result.stdout.contains("Plugins"));
        assertTrue(result.stdout.contains("Sandbox"));
        assertTrue(result.stdout.contains("Dummy Sum  [DummySumCommand]"));
        assertTrue(result.stdout.contains("(no menu path)"));
        // A command without a menu path is still listed in the package hierarchy
        assertTrue(result.stdout.contains("NoMenuCommand"));
    }

    // --- resilience to a partially resolvable classpath ---

    @Test
    public void describeCommandReportsUnloadableClassWithoutAbortingTheRun() {
        // In the field this happens when a plugin's own dependencies were not added to the
        // invocation, so reading its fields raises NoClassDefFoundError. That cannot be
        // staged here without classpath surgery, so UnloadableCommand fails in its static
        // initializer instead: a different LinkageError reaching the same handler. What
        // matters is that it is reported per class, and does not cost the other classes
        // their output.
        CLI.Result result = CLI.run(new String[]{"describe-command",
                "ch.unige.biochem.scijava.introspect.DummySumCommand",
                "ch.unige.biochem.scijava.introspect.UnloadableCommand",
                "ch.unige.biochem.scijava.introspect.DummySumCommand"});
        assertEquals(0, result.exitCode);

        JsonArray arr = JsonParser.parseString(result.stdout).getAsJsonArray();
        assertEquals(3, arr.size());

        assertTrue(arr.get(0).getAsJsonObject().has("input"));
        assertTrue(arr.get(2).getAsJsonObject().has("input"));

        JsonObject failed = arr.get(1).getAsJsonObject();
        assertEquals("ch.unige.biochem.scijava.introspect.UnloadableCommand",
                failed.get("name").getAsString());
        assertTrue(failed.get("error").getAsString().contains("could not be resolved"));
    }

    // --- parameter attributes ---

    private static JsonObject describeOne(String className) {
        CLI.Result result = CLI.run(new String[]{"describe-command", className});
        assertEquals(0, result.exitCode);
        return JsonParser.parseString(result.stdout).getAsJsonArray().get(0).getAsJsonObject();
    }

    private static JsonObject input(JsonObject command, String name) {
        for (JsonElement element : command.getAsJsonArray("input")) {
            if (element.getAsJsonObject().get("name").getAsString().equals(name)) {
                return element.getAsJsonObject();
            }
        }
        fail("No input " + name);
        return null;
    }

    @Test
    public void describeCommandReportsDefaultsChoicesAndAttributes() {
        JsonObject cmd = describeOne("ch.unige.biochem.scijava.introspect.DummyOptionsCommand");

        JsonObject format = input(cmd, "format");
        assertEquals("Decimal", format.get("default").getAsString());
        assertEquals(2, format.getAsJsonArray("choices").size());
        assertEquals("Integer", format.getAsJsonArray("choices").get(0).getAsString());
        assertFalse(format.has("required"));

        JsonObject digits = input(cmd, "digits");
        assertEquals(3, digits.get("default").getAsInt());
        assertEquals("0", digits.get("min").getAsString());
        assertEquals("10", digits.get("max").getAsString());

        // NaN means 'not set': no default is reported
        assertFalse(input(cmd, "scale").has("default"));

        JsonObject output = input(cmd, "output");
        assertEquals("save", output.get("style").getAsString());
        assertFalse(output.get("required").getAsBoolean());
        assertFalse(output.has("default"));
    }

    @Test
    public void describeCommandReportsMessagesWithoutMarkup() {
        JsonObject cmd = describeOne("ch.unige.biochem.scijava.introspect.DummyOptionsCommand");
        assertEquals("Note: the number is rounded", cmd.getAsJsonArray("messages").get(0).getAsString());
        // the message item is not an input
        for (JsonElement element : cmd.getAsJsonArray("input")) {
            assertNotEquals("message", element.getAsJsonObject().get("name").getAsString());
        }
    }

    @Test
    public void dynamicCommandsAreOnlyDescribedOnRequest() {
        String dynamic = "ch.unige.biochem.scijava.introspect.DummyDynamicCommand";
        JsonObject snapshot = JsonParser.parseString(
                CLI.run(new String[]{"snapshot", "ch.unige.biochem.scijava.introspect"}).stdout).getAsJsonObject();
        assertFalse(snapshot.has(dynamic));

        JsonObject withDynamic = JsonParser.parseString(
                CommandIntrospector.describePackage("ch.unige.biochem.scijava.introspect", true)).getAsJsonObject();
        assertTrue(withDynamic.has(dynamic));
        assertTrue(withDynamic.getAsJsonObject(dynamic).get("dynamic").getAsBoolean());
        assertEquals("region", withDynamic.getAsJsonObject(dynamic).getAsJsonArray("input")
                .get(0).getAsJsonObject().get("default").getAsString());
    }
}
