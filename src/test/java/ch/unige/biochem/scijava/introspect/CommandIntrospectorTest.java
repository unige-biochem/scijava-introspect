/*-
 * #%L
 * A CLI for introspecting SciJava/ImageJ2 commands, used to build MCP servers and documentation for Fiji
 * %%
 * Copyright (C) 2026 University of Geneva, Department of Biochemistry
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */
package ch.unige.biochem.scijava.introspect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scijava.Context;
import org.scijava.command.CommandService;

import static org.junit.Assert.*;

public class CommandIntrospectorTest {

    private static Context context;

    @BeforeClass
    public static void createContext() {
        context = new Context(CommandService.class);
    }

    @AfterClass
    public static void disposeContext() {
        context.dispose();
    }

    private static JsonObject input(JsonObject description, String name) {
        for (JsonElement input : description.getAsJsonArray("input")) {
            if (input.getAsJsonObject().get("name").getAsString().equals(name)) {
                return input.getAsJsonObject();
            }
        }
        return null;
    }

    @Test
    public void commandsWithInitializersAreFlagged() {
        assertTrue(CommandIntrospector.describe(DummyInitializedCommand.class).get("hasInitializer").getAsBoolean());
        assertFalse(CommandIntrospector.describe(DummySumCommand.class).has("hasInitializer"));
        assertFalse(CommandIntrospector.describe(DummyDynamicCommand.class).has("hasInitializer"));
    }

    @Test
    public void staticDescriptionLacksInitializedValues() {
        JsonObject description = CommandIntrospector.describe(DummyInitializedCommand.class);
        assertFalse(input(description, "pixel_size").has("default"));
        assertFalse(input(description, "region").has("choices"));
        assertEquals("Not initialized", description.getAsJsonArray("messages").get(0).getAsString());
    }

    @Test
    public void initializedDescriptionHasComputedDefaultsChoicesAndMessages() {
        JsonObject description = CommandIntrospector.describeInitializedAsJson(context,
                DummyInitializedCommand.class, Collections.emptyMap());
        assertEquals(10, input(description, "pixel_size").get("default").getAsDouble(), 0);
        JsonArray choices = input(description, "region").getAsJsonArray("choices");
        assertEquals("cortex_mouse", choices.get(0).getAsString());
        assertEquals("cerebellum", choices.get(1).getAsString());
        assertEquals("Species: mouse", description.getAsJsonArray("messages").get(0).getAsString());
        // Annotation texts are kept
        assertEquals("Not set: the default of the species", input(description, "pixel_size").get("description").getAsString());
    }

    @Test
    public void presetInputsDriveTheInitializerAndAreLeftOut() {
        Map<String, Object> preset = new HashMap<>();
        preset.put("species", "rat");
        JsonObject description = CommandIntrospector.describeInitializedAsJson(context,
                DummyInitializedCommand.class, preset);
        assertNull(input(description, "species"));
        assertEquals(40, input(description, "pixel_size").get("default").getAsDouble(), 0);
        assertEquals("cortex_rat", input(description, "region").getAsJsonArray("choices").get(0).getAsString());
        assertEquals("Species: rat", description.getAsJsonArray("messages").get(0).getAsString());
    }

    @Test
    public void commandsWithoutInitializerKeepTheirStaticDefaults() {
        JsonObject described = CommandIntrospector.describe(DummySumCommand.class);
        JsonObject initialized = CommandIntrospector.describeInitializedAsJson(context,
                DummySumCommand.class, Collections.emptyMap());
        assertEquals(described.getAsJsonArray("input"), initialized.getAsJsonArray("input"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonPluginsAreRejected() {
        CommandIntrospector.describeInitializedAsJson(context, UnannotatedCommand.class, Collections.emptyMap());
    }

    public static class UnannotatedCommand implements org.scijava.command.Command {
        @Override
        public void run() {
        }
    }
}
