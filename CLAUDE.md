# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**scijava-introspect** is a Java CLI that introspects SciJava/ImageJ2 commands via reflection: it lists them,
describes their `@Parameter` inputs and outputs as JSON or Markdown, fetches their source from GitHub,
snapshots a package, and diffs two snapshots. It is the offline half of the tooling behind a Fiji MCP
server — **no running Fiji instance is ever needed**, only the target plugin classes on the classpath
(added at invocation time via jgo's `+` syntax).

Anything that requires a live ImageJ instance (running Groovy, driving the script editor, reporting
open images) belongs in the MCP server repository, not here.

## Build

Maven project inheriting from `pom-scijava` 43.0.0. Java 9 source/target.

```bash
mvn clean install        # build and install locally
mvn compile              # compile only
mvn test                 # run tests (JUnit 4)
```

The SciJava Maven repository (`https://maven.scijava.org/content/groups/public`) is required for
dependency resolution.

## Architecture

Main source lives in package `ch.unige.biochem.scijava.introspect`:

- **CLI** — the only entry point (`ch.unige.biochem.scijava.introspect.CLI`, also the pom's `main-class`).
  Parses the subcommand and delegates. `main` redirects `System.out` to `System.err` for the duration
  of `run()`, so that third-party logging cannot corrupt the machine-readable payload; only the payload
  is written to the real stdout at the end. All logic lives in the package-private `run(String[])`,
  which returns a `Result` (stdout / stderr / exit code) instead of exiting — that is what the tests
  drive.
- **CommandInvestigator** — the reflection layer. Discovers `Command` subclasses in a package via
  `Reflections`, and renders one command as JSON (`toJson`) or Markdown (`toMd`). Also fetches source
  code from GitHub via SciJava's `SourceFinder`.

Key patterns:
- Discovery skips abstract classes and interfaces (shared bases, not runnable commands), plus
  `InteractiveCommand` and `DynamicCommand`, whose parameters only exist at runtime.
- `@Parameter` fields of type `Service`, `Context` or `Button` are filtered out of the docs.
- Parameters are collected from the command **and its whole superclass chain**.
- GitHub URLs are rewritten to `raw.githubusercontent.com` for source fetching.
- All JSON serialization uses Gson with pretty printing.
- `tree` is the only subcommand that returns plain text rather than JSON.

## CLI Subcommands

| Subcommand | Args | Output |
|---|---|---|
| `list-commands` | `<package> [package2 ...]` | JSON array of fully qualified class names |
| `describe-command` | `<className> [className2 ...]` | JSON array of command descriptions (inputs, outputs, types) |
| `source-code` | `<className> [className2 ...]` | JSON object mapping class name to source code string |
| `snapshot` | `<package> [package2 ...]` | JSON object keyed by class name with full descriptions |
| `diff` | `<old.json> <new.json>` | JSON with `added`, `removed`, `modified`, `unchanged` arrays |
| `tree` | `<package> [package2 ...]` | Plain-text menu hierarchy and package hierarchy trees |

See `README.md` for the jgo invocations.

## Tests

- `CLITest` — JUnit 4 tests for every CLI subcommand, driving `CLI.run` / `CLI.diffSnapshots` /
  `CLI.buildTree` directly. One test (`sourceCodeFetchesActualSource`) is `@Ignore`d: it needs network
  access and a class published on GitHub.
- `DummySumCommand` — a test-scope fixture command with a known label, menu path, input and output.
  It is deliberately **not** in `src/main`, so the published jar adds no entry to Fiji's menus.
