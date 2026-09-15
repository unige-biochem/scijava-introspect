package ch.unige.biochem.scijava.introspect;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;

/**
 * A fixture for {@link CLITest} with the parameter attributes the descriptions report: a message,
 * choices, default values (including an unset NaN), an optional input, a style and bounds.
 */
@Plugin(type = Command.class,
        description = "Formats a number",
        menuPath = "Plugins>Sandbox>Dummy Options")
public class DummyOptionsCommand implements Command {

    @Parameter(visibility = ItemVisibility.MESSAGE)
    String message = "<html><b>Note:</b> the number is rounded</html>";

    @Parameter(label = "Format", choices = {"Integer", "Decimal"})
    String format = "Decimal";

    @Parameter(label = "Number of digits", min = "0", max = "10")
    int digits = 3;

    @Parameter(label = "Scale", description = "Not set: computed from the number")
    double scale = Double.NaN;

    @Parameter(label = "Output file", style = "save", required = false)
    File output;

    @Override
    public void run() {
    }
}
