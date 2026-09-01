package ch.unige.biochem.scijava.introspect;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * A minimal SciJava command, used only as a fixture for {@link CLITest}: it gives the
 * introspection subcommands something with a known label, menu path, input and output to
 * describe. It lives in test scope on purpose, so it never reaches Fiji's menus.
 */
@Plugin(type = Command.class,
        description = "Computes the sum of 2 integers",
        menuPath = "Plugins>Sandbox>Dummy Sum")
public class DummySumCommand implements Command {
    @Parameter(label = "First Number")
    Integer a;

    @Parameter(label = "Second Number")
    Integer b;

    @Parameter(type = ItemIO.OUTPUT)
    Integer c;

    @Override
    public void run() {
        c = a + b;
    }
}
