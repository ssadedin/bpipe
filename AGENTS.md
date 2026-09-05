# AGENTS.md — working on Bpipe

Bootstrapping context for agents (and humans) making changes to this repository. Everything below
was verified against this checkout; where a claim is environment-specific it says so.

## 1. What this is

Bpipe is a workflow engine for bioinformatics pipelines. Users write Groovy scripts made of *stages*
(closures) that run shell commands; Bpipe wires inputs/outputs, tracks dependencies, parallelises,
and journals the run. The product itself is a CLI (`bin/bpipe`) plus a JVM library.

* Language: **Groovy 3.0.10** (with a legacy 2.4 branch in `build.gradle`) and a handful of `.java` files.
* Runtime: **JDK 11** is the supported version (`ReleaseNotes.txt` 0.9.13, CI uses `java-version: '11'`).
* Version string: `project.ext.VERSION` in `build.gradle` (currently `0.9.14`).
* Distribution: one fat jar (`build/libs/bpipe-all.jar`, shadow) staged into `build/stage/bpipe-<version>`.

## 2. Getting to a buildable state (do this first)

Install and pin JDK 11 - example:

```bash
# Debian/Ubuntu container; sdkman ("sdk install java 11 ...") also works and is what CI uses for Groovy
sudo -n apt-get install -y --no-install-recommends openjdk-11-jdk-headless
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-arm64      # ...-amd64 on Intel
export PATH="$JAVA_HOME/bin:$PATH"
```

After changing JDKs, kill stale daemons:

```bash
./gradlew --stop
```

## 3. Build and run

```bash
./gradlew classes     # ~15s: compile only. Enough for iterating — bin/bpipe prepends build/classes/groovy/main
./gradlew stage       # ~35s: classes + shadow jar + build/stage/bpipe-0.9.14. Needed ONCE before any run
./gradlew dist        # stage + zip
./gradlew test        # unit tests (JUnit 4, from test-src/)
```

`./gradlew stage` is a prerequisite even for hand-run tests: `bin/bpipe` launches via
`org.codehaus.groovy.tools.GroovyStarter`, which lives in the fat jar. Without it every invocation fails
with `Could not find or load main class org.codehaus.groovy.tools.GroovyStarter`.

Incremental `classes` **never removes stale output**: if you rename or delete a source file, the old
`.class` stays in `build/classes/groovy/main` (and inside a previously built `bpipe-all.jar`), so a class
you think you removed keeps resolving. Run `./gradlew clean stage` after renames/deletions, or when
behaviour seems to contradict the source.

Run Bpipe straight from the source tree (it detects `build/classes` + `build/libs` and uses them):
```bash
cd /tmp/scratch && /path/to/repo/bin/bpipe run -n 5 my_pipeline.groovy input.txt
```

Useful flags while developing (full list: `cli.with { … }` in `Runner.groovy`, or `bpipe run --help`):
`-n <threads>` concurrency, `-m <MB|GB>` memory limit, `-l name=N` limit a named resource, `-r` generate
the HTML report (e2e tests are expected to produce it), `-t` test mode, `-u <stage>` run until a stage,
`--dev <stage>` interactive re-run of one stage, `-p k=v` parameters, `-b <branches>` restrict branches,
`-d <dir>` output directory, `-v` verbose internal logging to stderr.

After a failed run look at `.bpipe/bpipe.log` and `.bpipe/logs/*.log` (`grep -a`, see §9), `bpipe errors`
(output of failed commands), and `.bpipe/outputs` (per-output metadata).

## 4. Tests

Two suites, both must pass.

**Unit tests** — `test-src/**/*.groovy`, JUnit 4, one class per source class (e.g. `ConcurrencyTest`,
`SQLiteOutputMetaDataStoreTest`, `processors/ThreadAllocationReplacerTest`). ~40s. Noise-suppressed
config in `build.gradle` prints only failures with full stacks.

**E2E ("functional") tests** — `tests/<name>/`, ~277 executed (dirs must start with a letter and contain
`run.sh`; `000-*`/`001-fixme-*` are skipped by design):

```bash
./gradlew e2eTest                                  # all of them: ~9 min at -Pe2eParallel=4
./gradlew e2eTest -Pe2eFilter=concurrency          # substring filter
./gradlew e2eTest -Pe2eParallel=4                  # groups run concurrently; CI uses 2
./gradlew e2eTestCore e2eTestParallel e2eTestResources e2eTestMisc ...   # named groups
```

Groups are prefix lists in `build.gradle` (`e2eTestGroups`: Core, Produce, Transform, From, Output,
Parallel, Branch, Sample, Check, Config, Load, Channels, Multi, Cleanup, Resources, Storage). **A new
`tests/` dir lands in `e2eTestMisc` unless its name matches a group prefix** — check `build.gradle` when
naming one. Each test has a **300 s timeout** in the runner; JUnit XML is written to
`build/test-results/e2eTests/e2e-results.xml` (that XML is hand-built and can be malformed — prefer the
console log).

Anatomy of a test directory (see `tests/concurrency_annotation/` for a small, recent example):

* `run.sh` — `source ../testsupport.sh`, then `bpipe run ... > test.out 2>&1` (directly, or via the
  `run` helper which also asserts an HTML report was generated), then `grep`/`err` assertions, ending in `true`.
* `test.groovy` — the pipeline. Inputs (`test.txt`, `.fastq`, …) sit alongside; optional `bpipe.config`,
  `cleanup.sh` (sourced by `testsupport.sh` before the run), and `*.sh` helpers.
* To run one by hand:

```bash
cd tests/<name> && rm -rf .bpipe doc times.txt && BASE=$PWD/.. bash run.sh; echo $?
```

`BASE` must be the `tests/` directory (the launcher path is derived from it). Always `rm -rf .bpipe`
first: Bpipe's history from a previous run changes what a test does (many rely on "output exists → skip").

Cleaning up after test runs (they create untracked outputs *and* chmod `cleanup.sh` files):

```bash
git clean -fdq -x tests/ && git checkout -- tests/    # re-add -e <your new test dir> if applicable!
```

## 5. Source map

| Path | Contents |
|---|---|
| `src/main/groovy/*.groovy` | default-package classes users reference directly: `Bpipe` (the facade, e.g. `Bpipe.run { … }`) and the stage **annotation types** (`Transform`, `Filter`, `Produce`, `Preserve`, `Intermediate`, `Accompanies`, `Concurrency`). Everything else is under `bpipe/` |
| `src/main/groovy/bpipe/Runner.groovy` | CLI entry, arg/config handling, **compiles the user script** |
| `.../Pipeline.groovy` | pipeline/branch execution, `declarePipelineStage`, `PIPELINE_IMPORTS`, `load` |
| `.../PipelineStage.groovy` | one stage instance: naming, body invocation, output interrogation |
| `.../PipelineContext.groovy` | the "magic" methods available inside a stage (`exec`, `produce`, `transform`, `filter`, `preserve`, `intermediate`, `accompanies`, `uses`, `concurrency`, `from`, `forward`, `check`, …) — ~4k lines |
| `.../PipelineDelegate.groovy` | `methodMissing`/`propertyMissing` glue: routes unqualified calls (incl. `*__bpipe_annotation`) to the context |
| `.../PipelineCategory.groovy` | DSL operators (`+`, `*`, `branch`, parallel segments, thread launching) |
| `.../Concurrency.groovy` | thread pools + resource semaphores (`-n`, `-m`, `limits`, `uses`, `@concurrency`/`StageLimiter`) |
| `.../ast/` | the AST transformations behind the stage annotations |
| `.../Config.groovy`, `src/main/config/bpipe.config` | configuration layers; defaults live in the packaged config file |
| `.../executor/`, `.../processors/`, `.../storage/` | command execution back-ends (local/SSH/Slurm/Torque/SGE/LSF/cloud/docker…), `$threads`/memory substitution, output storage abstraction |
| `.../Dependencies.groovy`, `.../*OutputMetaDataStore.groovy` | dependency/outcome tracking (SQLite is the current direction of work) |
| `.../cmd/` | `bpipe <subcommand>` implementations |
| `test-src/`, `tests/`, `docs/`, `examples/`, `plans/` | unit tests, e2e tests, mkdocs site, example pipelines, design notes |

## 6. How a user pipeline becomes code (know this before touching the DSL)

`Runner`/`Pipeline` **concatenate `Pipeline.PIPELINE_IMPORTS` in front of the script text** and compile it
at runtime with `GroovyClassLoader.parseClass`. That constant is therefore part of the language surface:

* it static-imports `Bpipe.*`, `PipelineChannel.*`, `bed`, `filetype`;
* it aliases the annotations to lowercase: `import Preserve as preserve; … import Concurrency as concurrency`.

Every statement in it **must end with `;`** — the next source token is glued directly after it, so a missing
semicolon produces a bizarre syntax error in user code.

`BpipeScriptBase` declares no-op stubs of the stage vocabulary (`exec`, `produce`, `transform`, `options`,
`uses`, `var`, …) so that *tooling* can compile pipeline scripts without running them — see
`bpipe generate-dsl` (`cmd/GenerateDSLCommand.groovy`) and `groovyc_bpipe_config.groovy` in the repo root,
which sets it as the script base class plus the `Filter`/`Transform` imports for a `groovyc -configscript`
compile. That config script is not referenced by `build.gradle` or `bin/*`: it is picked up by external
tooling/IDE flows, not by the runtime path above. **Add a stub there when you add a stage-level DSL word**
so that generated DSL and IDE checking stay in sync.

At run time, inside a stage body the closure delegate is a `PipelineDelegate`, whose `methodMissing`
resolves unknown names against the context, branch properties, and stage variables. This is why methods
"appear" inside pipelines with no visible receiver.

## 7. Concurrency / resource model (for anything touching `-n`, `uses`, `limits`)

Two layers, by design (see the class comment on `bpipe.Concurrency`):

1. **Physical**: tiered `ThreadPoolExecutor`s, one pool per branch depth (so nested stages cannot starve
   each other), sized from `Config.config.maxThreads` (`-n`).
2. **Logical**: `Concurrency.resourceAllocations`, a `Map<String,Semaphore>` of named resources — `threads`
   (default `maxThreads`), `memory` (`-m`/`maxMemoryMB`), `storage_space`, plus arbitrary names from the
   `limits` config / `-l name=N`. `uses(threads:1..10, GB:4, db:1)` declares a request; the actual amount is
   negotiated in `Concurrency.negotiateDynamicResources`/`allocateResources` (a fairness "auction" with a
   5 s window) so `$threads` splits capacity between running commands.
   Permits are taken in `processors/ThreadAllocationReplacer` (per **command**) and released by
   `executor/ThrottledDelegatingCommandExecutor`.
3. `@concurrency(N)` / `concurrency(N) { … }` adds a third, **per-stage-declaration** cap (`StageLimiter`,
   a fair semaphore) held for the whole body of a stage instance, acquired before it runs any command.

All of it is per-process: bpipe's master thread waits on remote jobs, so cluster/cloud executors are still
governed by it, but two separate `bpipe run` invocations know nothing about each other.

## 8. Environment gotchas (this class of dev container)

* **The working tree may be a virtiofs mount of a macOS host filesystem, so it is case-insensitive**
  (`git config core.ignorecase` is `true`). Files that differ only in case collide
* `grep` on large files from this mount can spuriously print `binary file matches` (same bytes copied to a
  local FS are fine). Use `rg` (installed) or `grep -a`; also note some sources are mode `755`.
* Outbound network is an allow-list proxy (`JAVA_TOOL_OPTIONS` already injects it). Reachable:
  `services.gradle.org`, `repo.maven.apache.org`, `plugins.gradle.org`. Blocked: `repo1.maven.org`,
  `api.adoptium.net`, most arbitrary hosts (`curl` prints `Blocked by network policy: domain …`). So:
  apt for JDKs, gradle for jars, sdkman for Groovy — but not Adoptium tarballs.
* JVM/process start-up is slow here, so allow plenty of margin: a pipeline that takes ~10 s of wall clock
  per stage can hit the runner's 300 s timeout, and tests that assert on exact file timestamps are best
  designed not to depend on them.
* `docs/index.md` is a symlink to `../README.md`; some tooling materialises it as a text file and it then
  shows up as a spurious local modification.
* `plans/` holds design notes (e.g. `STUB_FEATURE_PLAN.md`); several source comments refer to in-progress
  work on the SQLite output store, which is the active area of the branch lineage.

## 9. Conventions

* BSD licence header (match a neighbour file) and Groovy 3-compatible syntax; `@CompileStatic` is used
  on hot paths but **not** where dynamic dispatch to the delegate/context is required.
* Javadoc-ish comments are plentiful and chatty — explain *why*, existing files reference design
  decisions inline; match that tone rather than minimising comments.
* New user-visible language features need all three: `docs/Language/<Name>.md` + `mkdocs.yml` nav +
  an e2e test + a stub in `BpipeScriptBase` if it is a DSL statement.
* `ReleaseNotes.txt` is updated by the maintainer at release time; don't feel obliged to touch it.
* Do not commit test artefacts: after running suites, re-clean per §4 (`git clean -fdq -x tests/` plus
  `git checkout -- tests/`) and confirm `git status --short` lists only intended files.
