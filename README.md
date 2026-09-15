# scijava-introspect

A CLI for introspecting SciJava/ImageJ2 plugin commands — list them, describe their parameters, fetch source code, snapshot a version, and diff two versions. No running Fiji instance needed.

## Prerequisites

- Java 9+
- [jgo](https://github.com/scijava/jgo) on your PATH (tested against jgo 3.1.0)
- Build and install locally first:

```bash
mvn clean install
```

### Maven repositories

jgo 3.1.0's `-r name=url` flag corrupts the URL it is given: it strips the scheme and then
fails with `Invalid URL '//maven.scijava.org/...': No scheme supplied`. Declare the SciJava
repository in `~/.jgorc` instead, and drop `-r` from the command line:

```ini
[repositories]
scijava.public = https://maven.scijava.org/content/groups/public
```

### Required jgo flags

Both flags below are needed on every invocation; without them jgo fails before `main` runs.

| Flag | Why |
|---|---|
| `--class-path-only` | jgo 3.x splits jars between the module path and the classpath. On the module path `scijava-common` and `scijava-search` both export `org.scijava.plugin` to `reflections`, and the JVM aborts with `ResolutionException` while building the boot layer. This is a consequence of the deliberately narrow dependency set: nothing here needs JPMS. |
| `--lenient` | A transitive POM in the SciJava tree declares `com.yahoo.datasketches:memory:${project.parent.version}`, which jgo's resolver does not interpolate. The artifact is unused at runtime, so downgrading the failure to a warning is safe. |

### Endpoint syntax

The main class goes on the **first** artifact, and `+` dependencies follow it:

```
ch.unige.biochem:scijava-introspect:0.1.0:ch.unige.biochem.scijava.introspect.CLI+<group>:<artifact>:<version>
```

Putting the main class last (`...:0.1.0+<dep>:<MainClass>`) makes jgo read it as a
Maven *classifier* on the trailing dependency, which then fails to resolve:
`Artifact ch.epfl.biop:bigdataviewer-biop-tools:jar:ch.unige.biochem.scijava.introspect.CLI:0.21.0 not found`.

## Usage

All subcommands follow this pattern:

```bash
jgo -u --lenient --class-path-only \
  ch.unige.biochem:scijava-introspect:0.1.0:ch.unige.biochem.scijava.introspect.CLI \
  <subcommand> <args...>
```

The `-u` flag forces jgo to refresh its cache — use it after a new `mvn install`. Drop it for faster repeated calls once the cache is warm.

To add plugin dependencies without touching `pom.xml`, append them with `+`:

```bash
jgo -u --lenient --class-path-only \
  ch.unige.biochem:scijava-introspect:0.1.0:ch.unige.biochem.scijava.introspect.CLI+ch.epfl.biop:BIOP-ABBA:0.10.4 \
  list-commands ch.epfl.biop.atlas.aligner.command
```

## Subcommands

### list-commands

List all command class names in a package.

```bash
jgo -u --lenient --class-path-only \
  ch.unige.biochem:scijava-introspect:0.1.0:ch.unige.biochem.scijava.introspect.CLI \
  list-commands ch.epfl.biop.atlas.aligner.command
```

Returns a JSON array of fully qualified class names.

### describe-command

Get structured descriptions of one or more commands (inputs, outputs, types, labels, descriptions).

```bash
jgo -u --lenient --class-path-only \
  ch.unige.biochem:scijava-introspect:0.1.0:ch.unige.biochem.scijava.introspect.CLI \
  describe-command ch.epfl.biop.atlas.aligner.command.ABBAStartCommand
```

Returns a JSON array:

```json
[
  {
    "name": "ch.epfl.biop.atlas.aligner.command.ABBAStartCommand",
    "menuPath": "Plugins>BIOP>Atlas>ABBA - Align Big Brains and Atlases (no GUI)",
    "description": "Starts an ABBA session without graphical user interface...",
    "messages": [ "Select the atlas slicing orientation: click a preset or set the anatomical direction of each axis." ],
    "input": [
      { "type": "Atlas", "name": "ba", "label": "Atlas" },
      { "type": "String", "name": "x_axis", "label": "X axis (sections, left to right)",
        "choices": [ "AP (Anterior-Posterior)", "PA (Posterior-Anterior)", "..." ] }
    ],
    "output": [
      { "type": "MultiSlicePositioner", "name": "mp", "label": "ABBA session" }
    ]
  }
]
```

Besides `type` and `name`, an input may report `label`, `description`, `default` (read from a new
instance of the command), `choices`, `required: false`, `style`, `min` and `max`. The texts of the
message items, without HTML markup, are listed in `messages`.

A command whose defaults or choices can be computed before its dialog shows (it declares
`initialize()`, or an initializer method in `@Plugin` or `@Parameter`) is flagged with
`"hasInitializer": true`: the values above are those of a new instance, before initialization.

#### Initialized description (Java API)

To see the values a caller gets, including the ones computed by initializers (choices filled at
runtime, defaults depending on another input, inputs added by a `DynamicCommand`), describe the
command from a running SciJava context:

```java
String json = CommandIntrospector.describeInitialized(context, MyCommand.class,
        Collections.singletonMap("image", image));
```

The command is created, the preset inputs are set, then its initializers run, as SciJava does before
showing a dialog; the command is not run. The result has the shape of one `describe-command` entry,
without the preset inputs. Initializers are the command's own code: they may be slow or have side
effects.

### source-code

Fetch the Java source code of one or more classes from GitHub.

```bash
jgo -u --lenient --class-path-only \
  ch.unige.biochem:scijava-introspect:0.1.0:ch.unige.biochem.scijava.introspect.CLI \
  source-code ch.epfl.biop.atlas.aligner.command.ABBAStartCommand
```

Returns a JSON object mapping class name to source string.

### snapshot

Generate a full snapshot of all commands in one or more packages. Useful as a baseline for version comparison.

```bash
jgo -u --lenient --class-path-only \
  ch.unige.biochem:scijava-introspect:0.1.0:ch.unige.biochem.scijava.introspect.CLI \
  snapshot ch.epfl.biop.atlas.aligner.command > snapshot.json
```

Returns a JSON object keyed by class name with full command descriptions.

### diff

Compare two snapshot files to find what changed between versions.

```bash
jgo -u --lenient --class-path-only \
  ch.unige.biochem:scijava-introspect:0.1.0:ch.unige.biochem.scijava.introspect.CLI \
  diff old.json new.json
```

Returns a JSON object with `added`, `removed`, `modified`, and `unchanged` arrays.

### tree

Display the menu hierarchy and package hierarchy of all commands in one or more packages as a human-readable tree.

```bash
jgo -u --lenient --class-path-only \
  ch.unige.biochem:scijava-introspect:0.1.0:ch.unige.biochem.scijava.introspect.CLI \
  tree ch.epfl.biop.atlas.aligner.command
```

Returns plain text with two sections:

```
=== Menu Hierarchy ===

├── Plugins
│   └── BIOP
│       └── ABBA
│           └── Start ABBA  [ABBAStartCommand]
...

=== Package Hierarchy ===

├── ch
│   └── epfl
│       └── biop
│           └── atlas
│               └── aligner
│                   └── command
│                       └── ABBAStartCommand  (menu: Start ABBA)
...
```

## Workflow for updating documentation

1. Snapshot the old version and the new version, then diff them:

```bash
jgo -u --lenient --class-path-only \
  ch.unige.biochem:scijava-introspect:0.1.0:ch.unige.biochem.scijava.introspect.CLI+ch.epfl.biop:BIOP-ABBA:0.9.0 \
  snapshot ch.epfl.biop.atlas.aligner.command > old.json

jgo -u --lenient --class-path-only \
  ch.unige.biochem:scijava-introspect:0.1.0:ch.unige.biochem.scijava.introspect.CLI+ch.epfl.biop:BIOP-ABBA:0.10.4 \
  snapshot ch.epfl.biop.atlas.aligner.command > new.json

jgo -u --lenient --class-path-only \
  ch.unige.biochem:scijava-introspect:0.1.0:ch.unige.biochem.scijava.introspect.CLI \
  diff old.json new.json
```

2. For **added** commands: create new documentation pages using `describe-command`.
3. For **modified** commands: run `describe-command` on both versions to see exactly what changed.
4. For **removed** commands: mark pages as deprecated or remove them.
5. **unchanged** commands need no documentation updates.

## Use in documentation repositories

To give an LLM access to this tool from another repository's `CLAUDE.md`, add a section like:

```markdown
## Fiji Command Introspection

You have access to a CLI tool that introspects Fiji/ImageJ plugin commands.
No running Fiji instance is needed. See the scijava-introspect README for full documentation.

Base invocation (run after `mvn clean install` in the scijava-introspect directory):

​```bash
jgo -u --lenient --class-path-only \
  ch.unige.biochem:scijava-introspect:0.1.0:ch.unige.biochem.scijava.introspect.CLI \
  <subcommand> <args...>
​```

Key subcommands: `list-commands`, `describe-command`, `source-code`, `snapshot`, `diff`, `tree`.
```

## License

MIT — see [LICENSE](LICENSE).
