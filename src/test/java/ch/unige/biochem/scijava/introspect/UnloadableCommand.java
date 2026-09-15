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
