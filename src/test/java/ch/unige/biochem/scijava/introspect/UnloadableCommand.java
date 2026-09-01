package ch.unige.biochem.scijava.introspect;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * A command that cannot be loaded: its static initializer always throws, so
 * {@code Class.forName} raises {@link ExceptionInInitializerError}.
 *
 * It stands in for the case that actually occurs in the field — a plugin class whose own
 * dependencies are absent from the classpath, which raises {@code NoClassDefFoundError}.
 * Both are {@link LinkageError}s and reach the same handler in {@link CLI}, and this one
 * needs no classpath surgery to reproduce.
 */
@Plugin(type = Command.class, menuPath = "Plugins>Sandbox>Unloadable")
public class UnloadableCommand implements Command {

    static {
        if (true) throw new IllegalStateException("deliberately unloadable");
    }

    @Parameter
    Integer a;

    @Override
    public void run() {
    }
}
