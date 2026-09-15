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

import java.util.Arrays;

import org.scijava.ItemVisibility;
import org.scijava.command.DynamicCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * A fixture for {@link CommandIntrospectorTest}: a command whose initializer computes a default from
 * another input, fills the choices of an input and rewrites its message, as SciJava does before
 * showing the dialog.
 */
@Plugin(type = org.scijava.command.Command.class, menuPath = "Plugins>Sandbox>Dummy Initialized")
public class DummyInitializedCommand extends DynamicCommand {

    @Parameter(visibility = ItemVisibility.MESSAGE)
    String message = "Not initialized";

    @Parameter(label = "Species")
    String species = "mouse";

    @Parameter(label = "Pixel size", description = "Not set: the default of the species")
    double pixel_size = Double.NaN;

    @Parameter(label = "Region")
    String region;

    @Override
    public void initialize() {
        if (Double.isNaN(pixel_size)) {
            pixel_size = "rat".equals(species) ? 40 : 10;
        }
        getInfo().getMutableInput("region", String.class).setChoices(Arrays.asList("cortex_" + species, "cerebellum"));
        message = "<html><b>Species:</b> " + species + "</html>";
    }

    @Override
    public void run() {
    }
}
