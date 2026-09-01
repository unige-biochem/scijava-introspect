package ch.unige.biochem.fiji.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
import org.scijava.command.DynamicCommand;
import org.scijava.command.InteractiveCommand;
import org.scijava.log.LogService;
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
import java.util.List;

/**
 * Reflection-based introspection of SciJava {@link Command} classes: discovery within a
 * package, and rendering of a command's {@link Parameter} inputs and outputs as JSON or
 * Markdown. Nothing here needs a running Fiji instance, only the commands themselves on
 * the classpath.
 */
public class CommandInvestigator {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Finds every runnable SciJava {@link Command} declared under the given package.
     * Abstract classes and interfaces are excluded, since they are shared bases rather than
     * commands a user can invoke. Interactive and dynamic commands are excluded too: their
     * parameters are built at runtime, so reflection over their declared fields would not
     * describe them faithfully.
     */
    public static List<Class<? extends Command>> getCommandsFromPackage(String packagePath) {
        Reflections reflections = new Reflections(packagePath);
        return
                reflections.getSubTypesOf(Command.class)
                        .stream()
                        .filter(clazz -> !Modifier.isAbstract(clazz.getModifiers()))
                        .filter(clazz -> !clazz.isInterface())
                        .filter(clazz -> !(InteractiveCommand.class.isAssignableFrom(clazz)))
                        .filter(clazz -> !(DynamicCommand.class.isAssignableFrom(clazz)))
                        .collect(Collectors.toList());
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
        Plugin plugin = commandClass.getAnnotation(Plugin.class);
        if (plugin != null) {
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

            List<Field> allFields = collectParameterFields(commandClass);
            jsonObject.add("input", toJsonFields(inputFields(allFields)));
            jsonObject.add("output", toJsonFields(outputFields(allFields)));

            jsonArray.add(jsonObject);
        }

        return GSON.toJson(jsonArray);
    }

    private static JsonArray toJsonFields(List<Field> fields) {
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
            array.add(fieldNode);
        });
        return array;
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
