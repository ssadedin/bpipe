# Execution-Time File Staging

## Objective

Let Bpipe move a command's files to an alternative location for the duration of the command, and back again, without the pipeline needing to know:

1. Before the command runs, its inputs are staged into a *staging area*.
2. The command text is rewritten so that it reads inputs from the staging area and writes outputs there.
3. After the command succeeds, the declared outputs are moved back to their normal (canonical) locations, where Bpipe's dependency tracking expects them.
4. The staged copies are discarded in every case.

Motivating cases:

- **HPC node-local scratch (first target).** Primary storage is slow and shared; each compute node has fast local SSD (for example `/scratch`). Today users write the copying into the command by hand.
- **S3 staging (second target).** Inputs are uploaded to a bucket *before* the compute instance exists, and tools read them as `s3://` URLs (see the `s3-transfer-mode` branch / `design/S3_TRANSFER_MODE.md`). The aim is for this to be a second staging-area type that works with the AWS EC2 executor with little or no executor-specific code.

Staging is **separate** from the cloud executors' `transfer` (rsync) feature. It runs at the per-command level that every executor goes through (`ThrottledDelegatingCommandExecutor` plus a `CommandProcessor`), not inside any one executor.

### Non-goals (v1)

- Fully dynamic scratch locations (for example `$TMPDIR` set per job). The staging area path is a constant in the config, which in practice is uniform across a cluster.
- Undeclared outputs. Only declared outputs are moved back; anything else written to the staging area is discarded.
- Keeping outputs after a failure. Staged files are always discarded, and Bpipe cleans up failed outputs anyway.
- Containers (Docker/Singularity). Combining staging with a container is rejected with an error in v1 (see *Open Points*).
- Derived paths such as `$input.prefix`, or paths built by hand inside the command. These are not rewritten (see *Safety property*).

## Configuration

```groovy
stagingAreas {
    scratch {
        type = 'directory'
        path = '/scratch'
        // optional: extensions of companion files to stage with a referenced input
        // companions = ['.bai', '.crai', '.csi', '.tbi', '.fai', '.dict', '.gzi', '.idx']
    }
}

// for all commands ...
staging = 'scratch'

// ... or only for I/O-heavy ones
commands {
    sort_bam { staging = 'scratch' }
}
```

`staging` is resolved per command like any other command setting. `stagingAreas` is a separate top-level block, not part of `filesystems`, because every `filesystems` entry is also searched when Bpipe resolves inputs (`StorageLayer.resolve`), and a staging area must not take part in that.

## Design Overview

### Lifecycle

```
ThrottledDelegatingCommandExecutor.doStart
  prepareCommand → runProcessors
      ... existing processors (env, conda, uv, memory, threads, StorageResolver)
      StagingProcessor              ← plans staging, rewrites paths, adds JOB-layer hooks
  render layer hooks                ← CommandLayers.render around the final command text
  [test mode break]                 ← bpipe test shows the staged command, nothing is moved
  area.beforeStart(plan)            ← host-side hook (no-op for 'directory'; S3 upload later)
  commandExecutor.start
ThrottledDelegatingCommandExecutor.waitFor
  waitWithReschedule
  area.afterComplete(plan, exit)    ← host-side hook (no-op for 'directory'; S3 download later)
```

There are two kinds of hook:

- **In-job shell** (prologue and epilogue around the command). These do all the work for a directory area, because node-local scratch only exists on the compute node, for the length of the job.
- **Host side**, run in the Bpipe JVM. These are unused for directory areas. S3 needs them: upload before the instance exists, download after it has been released.

The host-side hooks are added with the S3 area (step 6), not in the prototype.

### Key Decisions

**1. Staged paths mirror the canonical absolute path under a per-command directory.**
`stagedPath = <area.path>/bpipe-<user>-<pid>-<commandId>/<canonical absolute path>`

This is deterministic when the command is prepared, so the rewritten command contains plain absolute paths and no shell variables, which avoids quoting problems. It is unique across users, runs and concurrent commands on a node, and it is the same layout as the S3 key scheme. Paths get long, which is acceptable.

**2. Only rewrite whole-path tokens, and stage only what was rewritten.**
For each input and declared output, the rendered path (`PipelineFile.toString()`) and its absolute normalised form are replaced in the command text. A path is replaced only where it appears as a whole token, meaning it is bounded by the start or end of the text, whitespace, a quote, or `= , : ; ( ) < > | & \``. All candidates go into one pass, longest first, so that `x.bam` never matches inside `x.bam.bai` and replacements are never rewritten again.

The **inputs that are staged are exactly the ones that were rewritten, plus their companions** (decision 3). Stage inputs that the command never references are not copied. This matters in a stage with several commands, where each command would otherwise copy every input of the stage.

**Safety property:** anything that is not rewritten still points at primary storage and keeps working, just without the speed-up. A path built from parts (`$input.prefix`), or a reference genome passed in a variable, is read from primary storage as before. A declared output whose path was not rewritten is written straight to primary storage, and stage-out simply finds nothing to move.

**3. Companion files (indexes) are staged next to referenced inputs.**
Tools that read `x.bam` from scratch look for `x.bam.bai` or `x.bai` next to it on scratch. Bpipe pipelines often rely on such indexes being present on disk without them being stage inputs. So for each rewritten input, any file that **exists on disk** in the same directory and is named `<name><ext>` or `<name minus last extension><ext>` is also staged, for each `ext` in the area's `companions` list (default: `.bai .crai .csi .tbi .fai .dict .gzi .idx`).

This uses a fixed list of extensions, not a prefix wildcard, so that `x.bam` does not drag in `x.fastq.gz`, `x.sorted.bam` and so on.

**4. Staging is expressed as shell hooks on command layers (see *Command Layers* below).**
A directory area adds one hook to the `JOB` layer. Its *pre* part creates the staging directories and copies inputs in. Its *post* part moves declared outputs back if `$__bpipe_rc` is 0, then always removes the stage directory.

- A failure to stage in or stage out fails the command with a clear message on stderr. Otherwise the command's own exit status is kept.
- Outputs are moved back with `mv` (copy and delete across filesystems) only when the command succeeded. They are always deleted otherwise.

**5. `StagingProcessor` placement.**
It runs after `StorageResolver`, so `{bpipe:...}` storage tokens are already resolved. It rewrites the command text and registers its hooks; `ThrottledDelegatingCommandExecutor.runProcessors()` renders all hooks around the final text once every processor has run. If a container is configured as well, the result is a `PipelineError` until containers become layer boundaries (step 7).

**6. Area types are pluggable.**
`StagingArea` (abstract) holds the configured `name`, plus:

- `plan(Command, rewrittenInputs)` → `StagingPlan`: canonical-to-staged path maps for inputs (including companions) and outputs, and the stage directory.
- `String stagedPath(Path canonical, Command cmd)`: how a canonical path is presented to the command (`/scratch/.../abs/path` for a directory; `s3://bucket/prefix/abs/path` for S3 inputs).
- `void addHooks(StagingPlan plan, CommandLayers layers)`: registers the shell hooks that stage files in and out.
- `beforeStart(plan)`, `afterComplete(plan, exitCode)`: host-side hooks that do nothing by default.

`StagingAreas.create(name)` builds an area from `stagingAreas.<name>.type` (`directory` → `DirectoryStagingArea`, later `s3` → `S3StagingArea`). S3 inputs map to URLs, while S3 outputs map to a local directory on the instance and are uploaded by the epilogue. So the S3 area reuses the directory area's output handling.

### Limitations (accepted for v1)

- Derived and hand-built paths are not rewritten (see the safety property).
- A command that installs its own EXIT trap replaces Bpipe's, so stage-out does not run (the outputs are then missing and the command is failed by Bpipe's output checks).
- Free space on scratch is not checked.

## Command Layers

Staging needs shell to run in different places: copying on the compute host, outside any container; and (for S3) work in the Bpipe process itself. Rather than special-casing this, commands get an explicit layer model that any processor can contribute hooks to:

```
HOST   Bpipe JVM                         ← host hooks (step 8): beforeStart / afterComplete
  └─ executor boundary (SSH, sbatch, local fork)
JOB    job shell on the compute host     ← CommandLayer.JOB hooks
  └─ context boundary (container; step 7)
INNER  where the command runs            ← CommandLayer.INNER hooks
         └─ command text
```

- `bpipe.layers.ShellHook` has `pre` and `post` shell (see its javadoc for the contract). Pre hooks run in registration order and must `exit` non-zero on failure. Post hooks run on any exit, in reverse order, with errexit off, and see and may change the exit status in `$__bpipe_rc`. All hook shell must be POSIX `sh`.
- `bpipe.layers.CommandLayers` (a transient field on `Command`) collects hooks per layer and renders them: each layer that has hooks becomes a subshell with an EXIT trap running its post hooks, then its pre hooks, then the inner layer. A layer with no hooks renders as its inner part unchanged, so commands without hooks are exactly as before.
- The subshell keeps the trap contained. Executors that write the exit status in the same shell after the command (for example the AWS `cmd.sh` heredoc) record it after the post hooks have finished.
- Tested under `bash` and `dash`, with and without `-e`: ordering, exit-status propagation through both layers, a post hook failing the command, a failing pre hook skipping the command while post hooks still run, and heredocs in hooks and command.

Today `JOB` and `INNER` run in the same shell, because the container wrappers replace `command.shell` and so run *everything* inside the container. In step 7 the wrappers become boundaries: the `JOB` layer runs in the default shell and launches the container with the `INNER` layer fed in via a quoted heredoc. Mounts that hooks need (for example the staging area root) are added to the container automatically.

## Implementation Checklist

- [x] **1. Config + `StagingArea`/`StagingAreas`/`DirectoryStagingArea` + path mapping.** Unit tests: staged path layout, relative/absolute/`..` normalisation, unknown area or type errors.
- [x] **2. `StagingPathRewriter`** (pure string logic, returns the rewritten text and the set of files that matched). Unit tests: token boundaries, `x.bam` vs `x.bam.bai`, `I=x.bam`, `>out.txt`, quoted paths, relative and absolute forms, no double rewrite, `$input.prefix`-style text left alone.
- [x] **3. Companion detection** from disk. Unit tests with temp dirs: `x.bam.bai`, `x.bai`, `ref.fa.fai`, `ref.dict`, no wildcard matches.
- [x] **4. Directory wrapper generation.** Unit tests that **run the generated script with bash** against temp dirs: success moves outputs back and removes the stage dir; command failure, an explicit `exit 3` and `bash -e` failure all keep the status, discard staged files and leave no canonical output; stage-in failure gives a clear error.
- [x] **5. `StagingProcessor` wired into `ThrottledDelegatingCommandExecutor.runProcessors()`**, plus the container check. Functional tests:
  - `tests/staging_scratch`: a staged stage and an unstaged stage in one pipeline; the output is correct; stdout shows the command saw scratch paths; a companion index was present on scratch; the stage dir was removed; a re-run skips both stages.
  - `tests/staging_scratch_fail`: the command fails; the stage dir is removed; no canonical output.
- [x] **6. Command layer model.** `ShellHook`, `CommandLayer`, `CommandLayers` (rendering), rendered in `runProcessors()`; directory staging moved from a hand-built wrapper onto a `JOB` hook. Unit tests in `test-src/bpipe/layers/CommandLayersTest.groovy`.
- [ ] **7. Containers as layer boundaries.** Docker / Singularity contribute a launcher and mounts instead of replacing `command.shell`; the staging area root is mounted automatically; the AWS `/tmp/bpipe-aws` mount moves out of `DockerContainerWrapper` into a mount contribution. Lift the staging + container restriction. Add Docker functional tests first (there are none automated today). Review stopping containerised local jobs (container naming plus `docker rm -f`).
- [ ] **8. Host hooks** (`beforeStart` / `afterComplete`) in `ThrottledDelegatingCommandExecutor`.
- [ ] **9. S3 area:** the `ObjectStore` abstraction, `S3StagingArea` (upload in `beforeStart`, `aws s3 cp` of outputs as a `JOB` post hook, download in `afterComplete`), and cleanup on pipeline success. Carries over the decisions from `design/S3_TRANSFER_MODE.md` (dedup, `instanceProfile`, `AWS_REGION`, cleanup policy), plus credentials inside containers (the IMDSv2 hop limit for Docker on EC2).
- [ ] **10. Validate S3 staging with the AWS EC2 executor and `transfer=false`** (needs real AWS). Record any executor-specific changes that turned out to be needed.
- [ ] **11. Docs:** `docs/Guides/` page for staging.

### Prototype notes (steps 1–5)

- Code: `bpipe.staging.{StagingArea, StagingAreas, DirectoryStagingArea, StagingPlan, StagingPlanner, StagingPathRewriter, Companions}` and `bpipe.processors.StagingProcessor` (registered after `StorageResolver` in `ThrottledDelegatingCommandExecutor.runProcessors()`).
- `StagingProcessor` keeps the `area` and `plan` it applied, ready for the host-side hooks in step 6.
- Inputs are only candidates if they are regular files on local storage when the command is prepared; directories and missing files are left unrewritten.
- `staging` works from both the `stages { }` and the `commands { }` config blocks (covered by `tests/staging_scratch` and `tests/staging_scratch_fail` respectively).
- Staged paths are long (stage dir + full canonical path), for example `/scratch/bpipe-alice-1234-7/home/alice/project/x.bam`. This is harmless, but it shows in logs and in `bpipe test` output.

## Open Points

- Container support: decided. Staging runs in the `JOB` layer, outside the container, and the area root is mounted into it (step 7).
- User-configurable hooks (for example `pre_command` / `post_command` for the `JOB` or `INNER` layer, generalising the template-only `post_cmd`): not yet in scope.
- Whether `bpipe test` should show the staged command or the original one. v1 shows the staged one, because it is what will actually run.
- Reconnecting to a running command after Bpipe restarts: harmless for directory areas (all in-job); for S3, `afterComplete` must also run on that path (step 9).
