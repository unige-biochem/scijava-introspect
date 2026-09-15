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
