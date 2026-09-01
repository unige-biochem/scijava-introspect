# fiji-tools

A CLI for introspecting SciJava/ImageJ2 plugin commands — list them, describe their parameters, fetch source code, snapshot a version, and diff two versions. No running Fiji instance needed.

## Prerequisites

- Java 9+
- [jgo](https://github.com/scijava/jgo) on your PATH
- Build and install locally first:

```bash
mvn clean install
```

## Usage

All subcommands follow this pattern:

```bash
jgo -u \
  -r scijava=https://maven.scijava.org/content/groups/public \
  ch.unige.biochem:fiji-tools:0.1.0-SNAPSHOT:ch.unige.biochem.fiji.tools.CLI \
  <subcommand> <args...>
```

The `-u` flag forces jgo to refresh its cache — use it after a new `mvn install`. Drop it for faster repeated calls once the cache is warm.

To add plugin dependencies without touching `pom.xml`, append them with `+`:

```bash
jgo -u \
  -r scijava=https://maven.scijava.org/content/groups/public \
  ch.unige.biochem:fiji-tools:0.1.0-SNAPSHOT+ch.epfl.biop:BIOP-ABBA:0.10.4:ch.unige.biochem.fiji.tools.CLI \
  list-commands ch.epfl.biop.atlas.aligner.command
```

## Subcommands

### list-commands

List all command class names in a package.

```bash
jgo -u \
  -r scijava=https://maven.scijava.org/content/groups/public \
  ch.unige.biochem:fiji-tools:0.1.0-SNAPSHOT:ch.unige.biochem.fiji.tools.CLI \
  list-commands ch.epfl.biop.atlas.aligner.command
```

Returns a JSON array of fully qualified class names.

### describe-command

Get structured descriptions of one or more commands (inputs, outputs, types, labels, descriptions).

```bash
jgo -u \
  -r scijava=https://maven.scijava.org/content/groups/public \
  ch.unige.biochem:fiji-tools:0.1.0-SNAPSHOT:ch.unige.biochem.fiji.tools.CLI \
  describe-command ch.epfl.biop.atlas.aligner.command.ABBAStartCommand
```

Returns a JSON array:

```json
[
  {
    "name": "ch.epfl.biop.atlas.aligner.command.ABBAStartCommand",
    "description": "Starts ABBA from an Atlas",
    "input": [
      { "type": "Atlas", "name": "ba" },
      { "type": "String", "name": "x_axis" }
    ],
    "output": [
      { "type": "MultiSlicePositioner", "name": "mp" }
    ]
  }
]
```

### source-code

Fetch the Java source code of one or more classes from GitHub.

```bash
jgo -u \
  -r scijava=https://maven.scijava.org/content/groups/public \
  ch.unige.biochem:fiji-tools:0.1.0-SNAPSHOT:ch.unige.biochem.fiji.tools.CLI \
  source-code ch.epfl.biop.atlas.aligner.command.ABBAStartCommand
```

Returns a JSON object mapping class name to source string.

### snapshot

Generate a full snapshot of all commands in one or more packages. Useful as a baseline for version comparison.

```bash
jgo -u \
  -r scijava=https://maven.scijava.org/content/groups/public \
  ch.unige.biochem:fiji-tools:0.1.0-SNAPSHOT:ch.unige.biochem.fiji.tools.CLI \
  snapshot ch.epfl.biop.atlas.aligner.command > snapshot.json
```

Returns a JSON object keyed by class name with full command descriptions.

### diff

Compare two snapshot files to find what changed between versions.

```bash
jgo -u \
  -r scijava=https://maven.scijava.org/content/groups/public \
  ch.unige.biochem:fiji-tools:0.1.0-SNAPSHOT:ch.unige.biochem.fiji.tools.CLI \
  diff old.json new.json
```

Returns a JSON object with `added`, `removed`, `modified`, and `unchanged` arrays.

### tree

Display the menu hierarchy and package hierarchy of all commands in one or more packages as a human-readable tree.

```bash
jgo -u \
  -r scijava=https://maven.scijava.org/content/groups/public \
  ch.unige.biochem:fiji-tools:0.1.0-SNAPSHOT:ch.unige.biochem.fiji.tools.CLI \
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
jgo -u \
  -r scijava=https://maven.scijava.org/content/groups/public \
  ch.unige.biochem:fiji-tools:0.1.0-SNAPSHOT+ch.epfl.biop:BIOP-ABBA:0.9.0:ch.unige.biochem.fiji.tools.CLI \
  snapshot ch.epfl.biop.atlas.aligner.command > old.json

jgo -u \
  -r scijava=https://maven.scijava.org/content/groups/public \
  ch.unige.biochem:fiji-tools:0.1.0-SNAPSHOT+ch.epfl.biop:BIOP-ABBA:0.10.4:ch.unige.biochem.fiji.tools.CLI \
  snapshot ch.epfl.biop.atlas.aligner.command > new.json

jgo -u \
  -r scijava=https://maven.scijava.org/content/groups/public \
  ch.unige.biochem:fiji-tools:0.1.0-SNAPSHOT:ch.unige.biochem.fiji.tools.CLI \
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
No running Fiji instance is needed. See the fiji-tools README for full documentation.

Base invocation (run after `mvn clean install` in the fiji-tools directory):

​```bash
jgo -u \
  -r scijava=https://maven.scijava.org/content/groups/public \
  ch.unige.biochem:fiji-tools:0.1.0-SNAPSHOT:ch.unige.biochem.fiji.tools.CLI \
  <subcommand> <args...>
​```

Key subcommands: `list-commands`, `describe-command`, `source-code`, `snapshot`, `diff`, `tree`.
```
## License

MIT — see [LICENSE](LICENSE).
