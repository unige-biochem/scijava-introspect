package ch.unige.biochem.scijava.introspect;

import org.scijava.command.DynamicCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * A fixture for {@link CLITest}: a dynamic command, only described when dynamic commands are
 * requested.
 */
@Plugin(type = org.scijava.command.Command.class, menuPath = "Plugins>Sandbox>Dummy Dynamic")
public class DummyDynamicCommand extends DynamicCommand {

    @Parameter(label = "Name")
    String name = "region";

    @Override
    public void run() {
    }
}
