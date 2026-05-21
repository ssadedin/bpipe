# Stub Mode for Dev Execution

## Objective

Allow developers working on a pipeline to skip execution of stages whose tools they don't have installed, by creating empty "stub" output files that propagate downstream automatically. This enables working on a specific part of a pipeline without needing all upstream tools to be available.

## Design Overview

### How Stubs Are Triggered

In dev mode, when a `PipelineDevRetry` is thrown and the user is presented with the "Waiting for changes or \<enter\> to continue" prompt, they have an additional option — typing "stub" — to stub the current stage instead of re-executing it.

The interaction point is `PipelineStage.waitForDevInteraction()`, where the dev loop waits for user input. The response file `.bpipe/dev_continue` can contain the text "stub" to signal this intent.

### How Stub Files Are Created

When stubbing is chosen:

1. The outputs for the stage are already determined during the probe phase in `PipelineContext.produceImpl()` — the `fixedOutputs` and inferred outputs are computed before execution.
2. Each output file is created as an empty file (touched).
3. The files are marked as stubs in their output metadata.

The relevant code path is in `PipelineContext.execImpl()` where the `PipelineDevRetry` is thrown. Instead of retrying, the stub path:
- Creates the output files (touch them)
- Skips the actual command execution
- Continues the pipeline as if the command succeeded

### How Stubs Are Marked/Persisted

Bpipe already saves output metadata via `Dependencies.saveOutputs()` (called from `PipelineStage.saveOutputMetaData()`). Each output gets an `OutputMetaData` properties file in `.bpipe/outputs/`.

A `stub=true` property is added to the `OutputMetaData`. This:
- Survives pipeline restarts
- Is queryable when downstream stages check their inputs
- Allows cleanup/re-execution when the user is ready to run for real

### How Stub Status Propagates Downstream

At command execution time in `PipelineContext.execImpl()` or `async()`, before executing a command, check if *any* of the resolved inputs are stubs. If so, automatically stub the current stage's outputs too. This is checked via `Dependencies.instance` looking up the `OutputMetaData` for each input.

The check looks something like:

```groovy
// In PipelineContext.execImpl(), after resolving inputs:
if(hasStubInputs(actualResolvedInputs)) {
    return createStubOutputs(command, checkOutputs)
}
```

Where `hasStubInputs` queries the output graph:

```groovy
boolean hasStubInputs(List<PipelineFile> inputs) {
    Dependencies.instance.withOutputGraph { graph ->
        inputs.any { inp ->
            OutputMetaData props = graph.propertiesFor(inp)
            props?.stub == true
        }
    }
}
```

This approach is preferred over checking at stage initialization because it respects the existing flow — the probe still runs, outputs are determined, and then at the point of execution the decision is made to stub instead.

### How Retry Remembers Stub Status

Since stubs are recorded in `OutputMetaData`, on retry:
- If the user hasn't changed anything, the stub outputs exist and are "up to date" relative to their inputs, so the stage is skipped normally.
- If the user wants to un-stub (actually run), they can use a command like `bpipe unstub` that removes the stub flag and deletes the dummy files, forcing re-execution.

### Non-Dev Mode Behavior

When not running in dev mode, stub files should be treated as out-of-date by `Dependencies.getOutOfDate()` so that the pipeline re-runs them properly when the real tools are available.

### Integration Points

| Component | Change |
|-----------|--------|
| `PipelineStage.waitForDevInteraction()` | Handle "stub" response from user |
| `PipelineContext.execImpl()` | Check for stub inputs, create stub outputs |
| `OutputMetaData` | Add `stub` boolean property |
| `Dependencies.saveOutputs()` | Persist stub flag |
| `Dependencies.getOutOfDate()` | Treat stubs as always out-of-date when not in dev mode |
| Dev mode UI | Show stub option in prompt, display stub status |

### Stub With File (`stub <file>`)

When the user responds with `stub <file>` instead of plain `stub`, Bpipe copies the specified file
to the expected output path. Crucially, this output is **not** marked as a stub in metadata. This means:

- Downstream stages see a real file as input, not a stub
- `hasStubInputs()` returns false for downstream stages
- Downstream stages proceed through normal dev interaction and actually execute their commands
- In non-dev mode, the output is treated as up-to-date (it's a real file with real content)

This enables a workflow where the developer provides a sample/example output for one stage and then
tests that downstream stages actually work correctly with realistic input.

#### Implementation Details

1. **Parsing**: In `waitForDevInteraction()`, if the response starts with "stub " followed by a path,
   set `stubbedWithFile = <path>` on the PipelineStage (distinct from `stubbed = true`).

2. **File copy**: A new method `PipelineContext.createFileStubOutputs(outputs, sourceFile)`:
   - Validates source file exists (re-prompts if not)
   - Copies source to the first output path
   - If multiple outputs, touches remaining as empty stubs
   - Does NOT set `stubMode = true`
   - Creates a tracked command with text `<stub:filename>` for audit trail
   - Sets raw output on context

3. **Downstream behavior**: Since `stub=false` in metadata, downstream stages behave normally —
   they pause for dev interaction and execute commands for real.

4. **Edge cases**:
   - File not found → print error, stay in wait loop, re-prompt
   - Multiple outputs → copy to first, touch rest as empty stubs (with stub=true)
   - Relative paths → resolved relative to working directory

### Other Edge Cases

- **Glob outputs in produce**: The probe resolves what files *would* be created. May need user hints for complex globs.
- **Commands with side effects**: Stubbing assumes the only important outputs are files. Commands that update databases, etc., would not be properly stubbed.
- **Non-dev mode**: Stub files are treated as out-of-date so the pipeline re-runs them properly.
- **Filter/transform naming**: The probe already computes output names correctly, so this works without changes.

## Implementation Steps

- [x] Add `stub` field to `OutputMetaData` (persisted as a property in the metadata file)
- [x] Add stub detection utility method (`hasStubInputs`) that checks if any resolved inputs are stubs
- [x] Implement stub file creation logic (touch files + save metadata with `stub=true`)
- [x] Wire stub option into `PipelineStage.waitForDevInteraction()` (detect "stub" in response file)
- [x] Add automatic stub propagation check in `PipelineContext.execImpl()` before command execution
- [x] Ensure non-dev mode treats stubs as out-of-date in `Dependencies.getOutOfDate()`
- [ ] Add `bpipe unstub` command to clear stub state and delete stub files
- [x] Add functional test in `tests/` directory following existing test conventions
- [ ] Parse `stub <file>` response in `waitForDevInteraction()` — distinguish from plain `stub`
- [ ] Add `stubbedWithFile` field to `PipelineStage`
- [ ] Implement `PipelineContext.createFileStubOutputs(outputs, sourceFile)` — copies file, tracks command, does NOT set stubMode
- [ ] Handle the `stubbedWithFile` case in `PipelineStage.run()` catch block
- [ ] Add error handling for missing source file (re-prompt)
- [ ] Update test to cover `stub <file>` scenario
