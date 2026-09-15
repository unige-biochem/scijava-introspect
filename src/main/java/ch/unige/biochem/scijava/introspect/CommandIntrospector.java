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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import org.reflections.Reflections;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.CommandInfo;
import org.scijava.command.CommandService;
import org.scijava.command.DynamicCommand;
import org.scijava.command.InteractiveCommand;
import org.scijava.log.LogService;
import org.scijava.module.MethodCallException;
import org.scijava.module.Module;
import org.scijava.module.ModuleItem;
import org.scijava.module.ModuleService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Plugin;
import org.scijava.search.SourceFinder;
import org.scijava.search.SourceNotFoundException;
import org.scijava.service.Service;
import org.scijava.widget.Button;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reflection-based introspection of SciJava {@link Command} classes: discovery within a
 * package, and rendering of a command's {@link Parameter} inputs and outputs as JSON or
 * Markdown. Nothing here needs a running Fiji instance, only the commands themselves on
 * the classpath.
 */
public class CommandIntrospector {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Finds every runnable SciJava {@link Command} declared under the given package.
     * Abstract classes and interfaces are excluded, since they are shared bases rather than
     * commands a user can invoke. Interactive and dynamic commands are excluded too: their
     * parameters are built at runtime, so reflection over their declared fields would not
     * describe them faithfully.
     */
    public static List<Class<? extends Command>> getCommandsFromPackage(String packagePath) {
        return getCommandsFromPackage(packagePath, false);
    }

    /**
     * @param includeDynamic whether to keep {@link DynamicCommand}s: their declared parameters are
     *        described, but inputs added at runtime and choices set at runtime are missing. They are
     *        flagged with {@code "dynamic": true} in the JSON description.
     * @see #getCommandsFromPackage(String)
     */
    public static List<Class<? extends Command>> getCommandsFromPackage(String packagePath, boolean includeDynamic) {
        Reflections reflections = new Reflections(packagePath);
        return
                reflections.getSubTypesOf(Command.class)
                        .stream()
                        .filter(clazz -> !Modifier.isAbstract(clazz.getModifiers()))
                        .filter(clazz -> !clazz.isInterface())
                        .filter(clazz -> !(InteractiveCommand.class.isAssignableFrom(clazz)))
                        .filter(clazz -> includeDynamic || !(DynamicCommand.class.isAssignableFrom(clazz)))
                        .sorted(Comparator.comparing(Class::getName))
                        .collect(Collectors.toList());
    }

    /**
     * Describes every command of a package, see {@link #toJson(Class)}. A command whose class
     * cannot be resolved is described by an error instead of aborting the whole description.
     *
     * @return a JSON object keyed by fully qualified class name
     */
    public static String describePackage(String packagePath, boolean includeDynamic) {
        return GSON.toJson(describePackageAsJson(packagePath, includeDynamic));
    }

    static JsonObject describePackageAsJson(String packagePath, boolean includeDynamic) {
        JsonObject result = new JsonObject();
        for (Class<? extends Command> command : getCommandsFromPackage(packagePath, includeDynamic)) {
            try {
                JsonObject description = describe(command);
                if (description != null) {
                    result.add(command.getName(), description);
                }
            } catch (LinkageError e) {
                JsonObject error = new JsonObject();
                error.addProperty("name", command.getName());
                error.addProperty("error", unresolvableMessage(e));
                result.add(command.getName(), error);
            }
        }
        return result;
    }

    /**
     * The class itself was found, but loading it or reading its fields needs a type that is
     * not on the classpath. This is the common shape of a missing plugin dependency, so it
     * is reported per class rather than being allowed to abort the whole run.
     */
    static String unresolvableMessage(LinkageError e) {
        return "Class could not be resolved, usually because a dependency is missing from the classpath: "
                + e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    public static String readContentFromURL(URL url) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
    }

    /**
     * @param commandClass the command to describe
     * @return a summary of a SciJava command as a JSON array holding a single object, or an
     *         empty array if the class carries no {@link Plugin} annotation
     */
    public static String toJson(Class<? extends Command> commandClass) {
        JsonArray jsonArray = new JsonArray();
        JsonObject description = describe(commandClass);
        if (description != null) {
            jsonArray.add(description);
        }
        return GSON.toJson(jsonArray);
    }

    /**
     * Describes a command: name, label, menu path, description, the texts of its message items,
     * then its inputs and outputs. Default values are read from a new instance of the command,
     * when it can be created with its no-argument constructor.
     *
     * @return the description, or null if the class carries no {@link Plugin} annotation
     */
    static JsonObject describe(Class<? extends Command> commandClass) {
        Plugin plugin = commandClass.getAnnotation(Plugin.class);
        if (plugin == null) {
            return null;
        }
        JsonObject jsonObject = new JsonObject();

        // Add the name, label, menuPath and description first
        jsonObject.addProperty("name", commandClass.getName());
        if (!plugin.label().isEmpty()) {
            jsonObject.addProperty("label", plugin.label());
        }
        String menuPath = resolveMenuPath(plugin);
        if (menuPath != null) {
            jsonObject.addProperty("menuPath", menuPath);
        }
        if (!plugin.description().isEmpty()) {
            jsonObject.addProperty("description", plugin.description());
        }
        if (DynamicCommand.class.isAssignableFrom(commandClass)) {
            jsonObject.addProperty("dynamic", true);
        }
        if (hasInitializer(commandClass)) {
            jsonObject.addProperty("hasInitializer", true);
        }

        List<Field> allFields = collectParameterFields(commandClass);
        Object instance = newInstance(commandClass);
        JsonArray messages = messages(allFields, instance);
        if (messages.size() > 0) {
            jsonObject.add("messages", messages);
        }
        jsonObject.add("input", toJsonFields(inputFields(allFields), instance));
        jsonObject.add("output", toJsonFields(outputFields(allFields), null));
        return jsonObject;
    }

    /**
     * Whether values can be computed when the command is initialized, before its dialog shows: the
     * command, or one of its superclasses outside SciJava, declares {@code initialize()}, or its
     * {@link Plugin} or one of its {@link Parameter}s names an initializer method. Such defaults and
     * choices are only described by {@link #describeInitialized(Context, Class, Map)}.
     */
    static boolean hasInitializer(Class<?> commandClass) {
        if (!commandClass.getAnnotation(Plugin.class).initializer().isEmpty()) {
            return true;
        }
        for (Class<?> c = commandClass; c != null && !c.getName().startsWith("org.scijava."); c = c.getSuperclass()) {
            if (Arrays.stream(c.getDeclaredMethods())
                    .anyMatch(m -> m.getName().equals("initialize") && m.getParameterCount() == 0)) {
                return true;
            }
        }
        return collectParameterFields(commandClass).stream()
                .filter(f -> f.isAnnotationPresent(Parameter.class))
                .anyMatch(f -> !f.getAnnotation(Parameter.class).initializer().isEmpty());
    }

    /**
     * Describes a command as a caller about to run it sees it: the command is created in the given
     * context, the preset inputs are set, then its initializers run, as SciJava does before showing
     * the dialog of a command. Defaults, choices and messages are read from the initialized command,
     * so values computed by the initializers (choices filled at runtime, defaults depending on another
     * input) are described, as well as inputs added at runtime by a {@link DynamicCommand}.
     * <p>
     * Initializers are the command's own code: they may be slow or have side effects. The command is
     * not run.
     *
     * @param presetInputs inputs set before the initializers run (e.g. the object the command acts on),
     *        left out of the description
     * @return the description of {@link #toJson(Class)}, as a single JSON object
     * @throws IllegalArgumentException if the class carries no {@link Plugin} annotation
     * @throws IllegalStateException if the command cannot be created or its initializer fails
     */
    public static String describeInitialized(Context context, Class<? extends Command> commandClass,
                                             Map<String, Object> presetInputs) {
        return GSON.toJson(describeInitializedAsJson(context, commandClass, presetInputs));
    }

    static JsonObject describeInitializedAsJson(Context context, Class<? extends Command> commandClass,
                                                Map<String, Object> presetInputs) {
        JsonObject description = describe(commandClass);
        if (description == null) {
            throw new IllegalArgumentException(commandClass.getName() + " has no @Plugin annotation");
        }
        CommandInfo info = context.getService(CommandService.class).getCommand(commandClass);
        if (info == null) {
            info = new CommandInfo(commandClass); // not in the plugin index
        }
        Module module = context.getService(ModuleService.class).createModule(info);
        if (module == null) {
            throw new IllegalStateException("Cannot create " + commandClass.getName() + ", see the log");
        }
        presetInputs.forEach((name, value) -> {
            module.setInput(name, value);
            module.resolveInput(name);
        });
        try {
            module.initialize();
        } catch (MethodCallException e) {
            throw new IllegalStateException("The initializer of " + commandClass.getName() + " failed", e);
        }

        Map<String, JsonObject> described = new LinkedHashMap<>();
        description.getAsJsonArray("input").forEach(node ->
                described.put(node.getAsJsonObject().get("name").getAsString(), node.getAsJsonObject()));
        JsonArray messages = new JsonArray();
        JsonArray inputs = new JsonArray();
        for (ModuleItem<?> item : module.getInfo().inputs()) {
            String name = item.getName();
            if (item.getVisibility() == ItemVisibility.MESSAGE) {
                Object text = module.getInput(name);
                if (text instanceof String) {
                    String plain = ((String) text).replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
                    if (!plain.isEmpty()) messages.add(plain);
                }
                continue;
            }
            JsonObject node = described.get(name);
            if (node == null) {
                // Added at runtime, or a service: services are never described
                if (Service.class.isAssignableFrom(item.getType()) || Context.class.equals(item.getType())) {
                    continue;
                }
                node = new JsonObject();
                node.addProperty("type", item.getType().getSimpleName());
                node.addProperty("name", name);
                if (item.getLabel() != null && !item.getLabel().isEmpty()) {
                    node.addProperty("label", item.getLabel());
                }
                if (item.getDescription() != null && !item.getDescription().isEmpty()) {
                    node.addProperty("description", item.getDescription());
                }
                described.put(name, node);
            }
            node.remove("default");
            addDefault(node, module.getInput(name));
            List<?> choices = item.getChoices();
            if (choices != null && !choices.isEmpty()) {
                JsonArray array = new JsonArray();
                choices.forEach(choice -> array.add(String.valueOf(choice)));
                node.add("choices", array);
            }
        }
        described.forEach((name, node) -> {
            if (!presetInputs.containsKey(name)) {
                inputs.add(node);
            }
        });
        description.remove("messages");
        if (messages.size() > 0) {
            description.add("messages", messages);
        }
        description.add("input", inputs);
        return description;
    }

    private static JsonArray toJsonFields(List<Field> fields, Object instance) {
        JsonArray array = new JsonArray();
        fields.forEach(f -> {
            JsonObject fieldNode = new JsonObject();
            fieldNode.addProperty("type", f.getType().getSimpleName());
            fieldNode.addProperty("name", f.getName());
            Parameter param = f.getAnnotation(Parameter.class);
            if (!param.label().isEmpty()) {
                fieldNode.addProperty("label", param.label());
            }
            if (!param.description().isEmpty()) {
                fieldNode.addProperty("description", param.description());
            }
            if (instance != null) {
                addDefault(fieldNode, readField(f, instance));
            }
            if (param.choices().length > 0) {
                JsonArray choices = new JsonArray();
                Arrays.stream(param.choices()).forEach(choices::add);
                fieldNode.add("choices", choices);
            }
            if (!param.required()) {
                fieldNode.addProperty("required", false);
            }
            if (!param.style().isEmpty()) {
                fieldNode.addProperty("style", param.style());
            }
            if (!param.min().isEmpty()) {
                fieldNode.addProperty("min", param.min());
            }
            if (!param.max().isEmpty()) {
                fieldNode.addProperty("max", param.max());
            }
            array.add(fieldNode);
        });
        return array;
    }

    /**
     * @return the texts of the message items, stripped of their HTML markup, in declaration order
     */
    private static JsonArray messages(List<Field> allFields, Object instance) {
        JsonArray messages = new JsonArray();
        if (instance == null) {
            return messages;
        }
        allFields.stream()
                .filter(f -> f.isAnnotationPresent(Parameter.class))
                .filter(f -> f.getAnnotation(Parameter.class).visibility() == ItemVisibility.MESSAGE)
                .map(f -> readField(f, instance))
                .filter(value -> value instanceof String)
                .map(value -> ((String) value).replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim())
                .filter(text -> !text.isEmpty())
                .forEach(messages::add);
        return messages;
    }

    /**
     * Adds the value as "default" if it is a plain value: a string, a boolean, a finite number,
     * an enum or a character. Unset values (null, NaN) and objects are left out.
     */
    private static void addDefault(JsonObject fieldNode, Object value) {
        if (value instanceof String || value instanceof Enum || value instanceof Character) {
            fieldNode.addProperty("default", value.toString());
        } else if (value instanceof Boolean) {
            fieldNode.addProperty("default", (Boolean) value);
        } else if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (!Double.isNaN(number) && !Double.isInfinite(number)) {
                fieldNode.addProperty("default", (Number) value);
            }
        }
    }

    /**
     * @return a new instance of the command, or null if it has no accessible no-argument
     *         constructor or if creating it fails: defaults and messages are then not described
     */
    private static Object newInstance(Class<?> commandClass) {
        try {
            Constructor<?> constructor = commandClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    private static Object readField(Field field, Object instance) {
        try {
            field.setAccessible(true);
            return field.get(instance);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @param commandClass the command to describe
     * @return a summary of a SciJava command as a piece of markdown formatted text
     */
    public static String toMd(Class<? extends Command> commandClass) {

        StringBuilder infos = new StringBuilder();

        Plugin plugin = commandClass.getAnnotation(Plugin.class);
        if (plugin != null) {
            infos.append("# ").append(commandClass.getName()).append("\n");
            if (!plugin.label().isEmpty()) {
                infos.append("Label: ").append(plugin.label()).append("\n");
            }
            if (!plugin.description().isEmpty()) {
                infos.append("Description: ").append(plugin.description()).append("\n");
            }

            List<Field> allFields = collectParameterFields(commandClass);

            infos.append("## Input\n");
            appendMdFields(infos, inputFields(allFields));

            infos.append("## Output\n");
            appendMdFields(infos, outputFields(allFields));

            infos.append("\n");
        }
        return infos.toString();
    }

    private static void appendMdFields(StringBuilder infos, List<Field> fields) {
        fields.forEach(f -> {
            Parameter param = f.getAnnotation(Parameter.class);
            infos.append(f.getType().getSimpleName()).append(" ").append(f.getName()).append(";");
            if (!param.label().isEmpty() || !param.description().isEmpty()) {
                infos.append(" //");
            }
            if (!param.label().isEmpty()) {
                infos.append(" Label: ").append(param.label()).append(";");
            }
            if (!param.description().isEmpty()) {
                infos.append(" Description: ").append(param.description()).append(";");
            }
            infos.append("\n");
        });
    }

    /**
     * Collects the declared fields of the command and of its whole superclass chain, minus
     * the ones that never describe a user-facing parameter (services, contexts, buttons).
     */
    private static List<Field> collectParameterFields(Class<?> commandClass) {
        List<Field> allFields = new ArrayList<>();
        for (Class<?> c = commandClass; c != null && c != Object.class; c = c.getSuperclass()) {
            allFields.addAll(Arrays.asList(filterSkippable(c.getDeclaredFields())));
        }
        return allFields;
    }

    private static List<Field> inputFields(List<Field> allFields) {
        return allFields.stream()
                .filter(f -> f.isAnnotationPresent(Parameter.class))
                .filter(f -> {
                    Parameter p = f.getAnnotation(Parameter.class);
                    return (p.type() == ItemIO.INPUT) || (p.type() == ItemIO.BOTH);
                })
                .filter(f -> f.getAnnotation(Parameter.class).visibility() != ItemVisibility.MESSAGE)
                .sorted(Comparator.comparing(Field::getName))
                .collect(Collectors.toList());
    }

    private static List<Field> outputFields(List<Field> allFields) {
        return allFields.stream()
                .filter(f -> f.isAnnotationPresent(Parameter.class))
                .filter(f -> {
                    Parameter p = f.getAnnotation(Parameter.class);
                    return (p.type() == ItemIO.OUTPUT) || (p.type() == ItemIO.BOTH);
                })
                .sorted(Comparator.comparing(Field::getName))
                .collect(Collectors.toList());
    }

    /**
     * Fetches the source of a class from where SciJava believes it is published, in
     * practice a GitHub URL rewritten to raw.githubusercontent.com. Requires network access,
     * and only works for classes shipped from a public GitHub repository.
     */
    public static String getSourceCode(Class<?> commandClass, Context context) {
        try {
            URL location = SourceFinder.sourceLocation(commandClass, context.getService(LogService.class));
            return readContentFromURL(convertToRawURL(location));
        } catch (SourceNotFoundException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static URL convertToRawURL(URL githubURL) throws MalformedURLException {
        String urlString = githubURL.toString();

        if (!urlString.contains("github.com")) {
            throw new IllegalArgumentException("Invalid GitHub URL format: " + urlString);
        }

        urlString = urlString.replace("github.com", "raw.githubusercontent.com");

        // The raw host serves the file directly, without the /blob/ or /refs/tags/ segment
        if (urlString.contains("/blob/")) {
            urlString = urlString.replace("/blob/", "/");
        } else if (urlString.contains("/refs/tags/")) {
            urlString = urlString.replace("/refs/tags/", "/");
        }

        return new URL(urlString);
    }

    private static Field[] filterSkippable(Field[] declaredFields) {
        return Arrays.stream(declaredFields)
                .filter((f) -> {
                    if (Service.class.isAssignableFrom(f.getType())) {
                        return false;
                    }
                    if (f.getType().equals(Context.class)) {
                        return false;
                    }
                    if (f.getType().equals(Button.class)) {
                        return false;
                    }
                    return true;
                }).toArray(Field[]::new);
    }

    /**
     * @return the menu path of a plugin as a {@code >}-separated string, taken from either
     *         form the annotation allows (menuPath or menu), or null if the plugin declares
     *         no menu entry
     */
    static String resolveMenuPath(Plugin plugin) {
        if (plugin == null) return null;
        if (!plugin.menuPath().isEmpty()) return plugin.menuPath();
        Menu[] menus = plugin.menu();
        if (menus.length == 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < menus.length; i++) {
            if (i > 0) sb.append(">");
            sb.append(menus[i].label());
        }
        return sb.toString();
    }
}
