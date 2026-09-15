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
