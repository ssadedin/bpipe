---
name: bpipe-stages
description: Write, modify, and test Bpipe pipeline stages (Groovy closures that run shell commands via exec). Use when creating or editing *.groovy Bpipe pipelines or stage libraries, wiring $input/$output variables, using transform/filter/produce/from, setting output directories, using $threads/$memory, configuring stages in bpipe.config (memory, procs, containers, bind storage), or testing pipelines with staged (pre-computed or dummy) outputs via --staged.
---

# Writing Bpipe pipeline stages

Bpipe pipelines are Groovy scripts. A **stage** is a named closure. It runs shell commands with
`exec`, and Bpipe connects the stages through the implicit `$input` and `$output` variables.

```groovy
// stages.groovy
align = {
    doc "Align reads with bwa mem"
    requires REF : "Reference FASTA (bwa-indexed)"
    var MIN_QUAL : 20                                  // overridable default

    exec """
        set -o pipefail

        bwa mem -t $threads $REF $input1.fastq.gz $input2.fastq.gz
            | samtools view -q $MIN_QUAL -b -o $output.bam -
    """, "bwa"                                         // "bwa" = config name in bpipe.config
}
```

```groovy
// pipeline.groovy
load 'stages.groovy'
run { '%_R*.fastq.gz' * [ align + sort_bam ] }
```

## 1. Required idioms

1. **Put stages in a separate file from the pipeline.** Keep stage definitions (`stages.groovy`,
   or one file per area) apart from the main script, which contains only `load '...'` and
   `run { ... }`. `load` resolves the path against the current directory first, then the
   pipeline script's directory. This lets you test each stage on its own:

   ```bash
   bpipe execute -s stages.groovy align sample_R1.fastq.gz sample_R2.fastq.gz
   bpipe execute -s stages.groovy 'align + sort_bam' s_R1.fastq.gz s_R2.fastq.gz   # quote expressions
   bpipe execute -t -s stages.groovy align ...      # -t: test mode, print commands without running them
   ```
   `-s` also accepts a complete pipeline file. Any `run {}` statement in that file is ignored.
   `bpipe execute` takes the same flags as `bpipe run` (`-p`, `-n`, `-m`, `-d`, `-r`, ...).

2. **Every stage must create at least one output file.** Bpipe tracks dependencies through
   outputs. A command with no declared output runs again on every run and gives the next stage
   nothing to resolve. If a tool has no natural output (it only does a check, an upload, or a
   print), create a synthetic one:

   ```groovy
   check_bam = {
       exec """
           set -o pipefail

           samtools quickcheck -v $input.bam 2>&1 | tee $output.txt
       """
       forward input.bam                       // pass the real data on, not the report
   }
   ```
   `set -o pipefail` is essential when you pipe into `tee`. Without it, the exit status of `tee`
   hides a failure of the tool.

## 2. Inputs, outputs and the dependency graph

- `$input` is the primary input: by default, the first output of the previous stage. For the
  first stage it is the files given on the command line.
- `$input.ext` (extension syntax) searches **backwards through all earlier stages' outputs** (most
  recent first, then the original inputs) for a file ending in `.ext`. Prefer this to a bare
  `$input`, because it keeps the stage correct if an upstream stage reorders its outputs.
- `$input1.ext`, `$input2.ext` give the 1st and 2nd file of that type. `$inputs.ext` gives all
  of them, space-separated. `input[0]` / `inputs.size()` / `for(f in inputs)` work in Groovy code.
- `$output.ext` declares an output and returns its name. Names are derived from the input and the
  stage name: `sample.csv` → `sample.<stage>.ext`. In parallel branches the branch name is
  inserted: `sample.<branch>.<stage>.ext`. Each distinct `$output.<ext>` in a stage is a separate
  output. `$output1.ext`, `$output2.ext` give several outputs of one type.
- The **first output becomes the next stage's `$input`**. Override this with
  `forward <files>` (e.g. `forward input.bam`).
- When a command's declared outputs exist and are newer than its resolved inputs, Bpipe **skips
  the command**. This is what makes re-runs incremental. Always reference real files through
  `$input`/`$output`; a hard-coded path is invisible to the dependency graph.
- When a command fails, its outputs are moved to `.bpipe/trash`, so you never get a half-written
  file.
- Special suffixes:
  - `$output.dir` / `$input.dir`: the directory, for tools that take an output folder.
  - `$output.prefix`: the name without its extension, for tools that append the extension
    themselves (the file Bpipe expects must still be created).
  - `$input.csv.optional`: blank if the input is missing. Add `.flag('--foo')` to emit
    `--foo <file>` only when the file exists.
  - `$output.tsv.optional`: the command does not fail if this file is not created.
- Extension syntax handles **one** extension only (`$input.fastq.gz` is invalid; use `$input.gz`
  or `from`).
- **Gotcha (Bpipe 0.9.14 and earlier):** `$output.log`, `$input.log` and `$inputs.log` resolve to
  Bpipe's internal logger, not a file (you will see files named `java.util.logging.Logger@...`).
  This is fixed in 0.9.15. On older versions, use a different extension (e.g. `.txt`, `.out`).

## 3. transform, filter, produce, from

These blocks give the outputs explicit names, and make the block transactional: if the block
fails, all of its outputs are moved to the trash.

```groovy
transform('bam') { exec "samtools view -b $input.sam > $output.bam" }   // x.sam -> x.bam
transform('.fastq.gz') to('_fastqc.zip') { exec "fastqc -o . $input.gz" } // custom suffix mapping
transform('*.fastq.gz') to('.fastq') { ... }   // '*.' maps ALL matching inputs, not only the first
transform(~'(.*)(.csv|.tsv)') to('$1.xlsx') { ... }                      // regex with groups
filter('sorted') { exec "samtools sort -o $output.bam $input.bam" }     // x.bam -> x.sorted.bam
produce('summary.txt') { exec "collate $inputs.txt > $output" }         // fixed name
produce('a.txt','b.txt') { ... $output1 ... $output2 ... }
from('*.vcf') produce('merged.vcf') { exec "merge $inputs.vcf > $output" }  // reshape inputs
from('txt','txt','csv') { exec "tool $input1 $input2 $input3 > $output.xml" }
```

- `transform`: replaces the extension. `filter`: adds a tag and keeps the extension.
  `produce`: uses the exact names you give. Write extension patterns with the leading dot
  (`'.csv'`), because a bare word can match more of the name than you intend.
- Annotation forms exist for whole stages: `@transform('bam')`, `@filter('sorted')`,
  `@produce('x.txt')`. A stage can have only **one** annotation.
- `from(pattern)` sets the block's inputs to the most recent upstream output(s) matching the
  pattern. A bare extension (`'csv'`) matches one file; `'*.csv'` matches all files from the
  last stage that produced CSVs, which is useful after a parallel section. Do not nest `from`
  blocks.
- `produce('*.txt')` (wildcard) collects any new matching files. Avoid it in parallel branches,
  because branches see each other's files.

## 4. exec

```groovy
exec "cmd $input.txt > $output.csv"
exec """
    set -o pipefail

    step1 $input.bam
        | step2 > $output.vcf

    tabix -p vcf $output.vcf.gz
""", "config_name"
```

- **Newlines are joined into one line by default.** Consecutive lines become one command, which
  is convenient for long commands that you would otherwise continue with `\`. Only a **blank
  line** (or an explicit `;`/`&&`) separates two shell statements. Two statements on adjacent
  lines silently merge into one broken command.
- In double and triple quotes, `$name` is interpolated by Groovy/Bpipe. A variable that Bpipe
  does not define (e.g. `$HOME`) passes through to bash. Escape a literal `$` as `\$`
  (`\$(date)`, `awk '{print \$1}'`). Use `${name}.txt` to append text right after a variable,
  because `$name.txt` means a property lookup.
- The optional second argument is the **config name** used to look up `commands { ... }` in
  `bpipe.config`. If you omit it, the first word of the command is used.
- A non-zero exit fails the stage and aborts the pipeline. Use `set -o pipefail` with pipes.

## 5. Variables: var, requires, def, and Groovy's var

| Form | Meaning |
|---|---|
| `var NAME : default` | **Bpipe** stage parameter (colon, map syntax). The default applies only if `NAME` was not already supplied by `stage.using(NAME: x)`, `-p NAME=x`, a global variable, or a branch variable. |
| `requires NAME : "description"` | Fails with a helpful message if `NAME` is not supplied by any of those routes. |
| `def x = ...` | Ordinary Groovy local variable, private to this stage invocation. Use this for scratch values. |
| `var x = ...` | **Groovy 3/Java local type inference, not the Bpipe `var`.** It behaves like `def`, with no default or override semantics. Do not confuse it with `var x : ...`. |
| `x = ...` (no keyword) inside a stage | Creates a **global** that cannot be reassigned afterwards. This is a bug magnet in parallel pipelines, so avoid it. |
| `branch.x = ...` | Branch variable (see below). |

Top-level `NAME = value` in a loaded file is a pipeline-wide constant: treat it as read-only.
Pass values per use with `run { align.using(MIN_QUAL: 30) }`.

## 6. Branch variables

```groovy
set_sample = {
    branch.sample = branch.name          // in a parallel branch, branch.name is the branch's key
}
call = {
    exec "caller --sample $sample $input.bam > $output.vcf"   // read without the 'branch.' prefix
}
```

`branch` evaluates to the branch name, for example the sample in `'%.bam' * [...]` or the
chromosome under `chr(...)`. Properties set on it are visible to all later stages in the same
branch and in its child branches. They are thread-confined, so they are the safe way to share
per-sample state. A branch variable must not have the same name as a stage.

## 7. Output directories

- `output.dir = "qc"`: changes the output directory **for this stage only**. Set it **before**
  any `$output` is evaluated (put it first in the stage).
- `branch.dir = "results/$branch"`: changes the directory for this stage **and every later stage
  in this branch** (and child branches). Use it to put each sample's outputs in its own folder.
- `bpipe run -d outdir ...`: the default output directory for the whole run.
- Bpipe creates the directory. Use `$output.dir` when the tool wants a folder rather than a file.

## 8. $threads and $memory

- `$threads`: Bpipe assigns a thread count at run time. With no `procs` configured, the count is
  negotiated from what is available under `-n` (default: number of cores) and shared between the
  commands running at the same time. A configured `procs` (an integer, or an unquoted range `2..8`) sets or
  bounds the value. Use `$threads` in commands instead of hard-coding counts.
- `$memory`: replaced with the configured `memory` **in GB (integer)**. Referencing it **requires**
  a `memory` entry for that stage or command in `bpipe.config`; otherwise Bpipe reports an error.
  Reserving it also throttles the command against `-m` (the total memory limit). Typical use:
  `java -Xmx${memory}g ...`.
- `uses(threads: 4, GB: 8, db: 1) { exec ... }` declares resource needs inline (custom names are
  limited with `-l db=2`). `@concurrency(N)` caps how many instances of a stage run at once.

## 9. bpipe.config (stage and command configuration)

`bpipe.config` sits next to the pipeline script, or in the run directory (which takes
precedence). Settings are layered **root → `stages { <stage name> }` → `commands { <config
name> }`**; the later layer wins.

```groovy
executor = "local"           // or slurm, torque, sge, lsf, pbspro ...
memory = "4"                  // default for all commands: plain integer = GB; "8g"/"8GB"/"512MB" accepted
procs = 1

stages {
    align {                   // keyed by STAGE name
        memory = "16"
        procs = 4..16         // Groovy range, UNQUOTED: $threads negotiated within bounds
                              // ("4-16" as a string also works; "4..16" quoted becomes 416!)
        walltime = "08:00:00"
        container = "bwa"     // refers to the containers section below
    }
}

commands {
    gatk {                    // keyed by exec config name (2nd exec arg, or first word of the command)
        memory = "24g"
        procs = 4
        queue = "long"
        env { JAVA_OPTS = "-Djava.io.tmpdir=/scratch" }
    }
}

filesystems {
    refdata { type = 'bind'; base = '/data/reference' }
    scratch { type = 'bind'; base = '/scratch' }
}

containers {
    bwa {
        type = 'singularity'              // or 'docker'
        image = 'bwa_0.7.17.sif'          // also searched in <pipeline dir>/containers/
        storage = 'refdata,scratch'       // bind these filesystems (comma list or Groovy list)
    }
    gatk {
        type = 'docker'
        image = 'broadinstitute/gatk:4.5.0.0'
        storage = 'refdata'
        inherit_user = true               // run as the host uid:gid (avoids root-owned outputs)
        // platform = 'linux/amd64'; shell = '/bin/sh'   // for images without bash
        // entryPoint = 'default'                      // use the image's entrypoint (off by default)
    }
}
```

- Only the **run directory** is bound into containers automatically. Any path outside it
  (references, databases, inputs elsewhere) needs a `bind` filesystem listed in that container's
  `storage`. Bind mounts use the same path inside and outside the container, so commands are
  unchanged.
- A root-level `container { type=...; image=... }` containerises every command. `execOptions`
  passes extra singularity flags (e.g. `"--nv"`). `singularityExecutable = "apptainer"`
  switches the binary.
- `conda_env = 'name'`, `uv_env`/`uv_project`, and `modules = "A/1,B/2"` are per-command
  alternatives to containers.
- Configuration values can be closures over the input files, e.g.
  `walltime = { files -> ... }`.
- `parameters { REF = '/data/ref.fa' }` in config (or `stages { x { parameters { ... } } }`)
  supplies variables. `--env prod` (or `export BPIPE_DEFAULT_ENVIRONMENT=prod`; the flag wins)
  selects an `environments { prod { ... } }` override block.
- Stages stay portable when commands never mention containers, schedulers, or thread and memory
  numbers. All of those belong in `bpipe.config`.

## 10. Testing and debugging checklist

- Run a stage alone: `bpipe execute -s stages.groovy <stage> <inputs...>` in a scratch directory.
  Add `-t` to see the resolved commands first. Use `-p NAME=value` for `requires`/`var` values.
- Delete `.bpipe/` (and stale outputs) between attempts. Otherwise Bpipe may skip commands whose
  outputs already look up to date.
- Inspect `commandlog.txt` (the exact commands run, after line joining), `bpipe errors`,
  `.bpipe/bpipe.log`, and `.bpipe/outputs/`.
- Check that the output names and locations match expectations, and that the next stage resolves
  the right `$input.<ext>`.
- To skip expensive upstream stages, or to present specific test data to later stages, use
  `--staged` (section 11).

## 11. Testing with staged outputs (`--staged`, Bpipe 0.9.15+)

`--staged <dir>` copies prepared files into place instead of running the commands that would
create them. Use it to skip slow or expensive stages during development, and to feed downstream
stages specific test data (a QC failure, an empty VCF, a truncated sample) without real
end-to-end data that produces it. Every command that runs a pipeline accepts it, including
`run`, `execute`, `test` and `retry`.

```bash
bpipe run --staged test_data/baseline pipeline.groovy s1_R1.fastq.gz s1_R2.fastq.gz
bpipe execute --staged staged -s stages.groovy 'align + call + qc_report' s1_R1.fastq.gz s1_R2.fastq.gz
bpipe run --staged test_data/qc_fail,test_data/baseline pipeline.groovy ...   # first dir wins, per file
```

- **The staging directory mirrors the run directory.** An output the pipeline would write to
  `<run dir>/align/s1.align.bam` is taken from `<staging dir>/align/s1.align.bam`. Output names
  include branch names, `output.dir`/`branch.dir` and `-d`, so do not guess them. Get the exact
  paths from `bpipe test` (`-t`), from `commandlog.txt` after a real run, or from the
  `Outputs are:` summary.
- **The easy way to build one** is to run the pipeline once for real on a small sample, then
  copy the expensive stages' outputs with their relative paths into the staging directory
  (`rsync -R` or `cp --parents`). Then edit or replace files to create scenarios.
- **It is all or nothing per command.** A command is staged only if *every* one of its outputs
  has a staged file. If only some do, Bpipe prints `WARNING: stage X has staged files for only
  some of its outputs ...` and runs the command for real. When you see that warning, add the
  missing files (secondary *declared* outputs such as `.bai`, `.tbi` and `.idx` are easy to forget).
- **Index files come along automatically.** When a command is staged, known index files for its
  outputs are copied too if they are in the same staging directory as the output, even when they
  are not declared outputs (for example, a `.bai` written by `samtools index` inside the same
  `exec`). Both naming forms are found: `x.bam.bai` and `x.bai`. The defaults are
  `bam`→`bai`/`csi`, `cram`→`crai`, `vcf`→`idx`, `vcf.gz`→`tbi`/`csi`, `bcf`→`csi`,
  `bed.gz`/`gff.gz`/`gff3.gz`/`gtf.gz`→`tbi`, `fa`/`fasta`→`fai`, and `fa.gz`/`fasta.gz`→`fai`/`gzi`.
  Add or override types in `bpipe.config`. Entries are merged with the defaults by extension, and
  an empty list turns one off:

  ```groovy
  staging {
      indexes = [ dat: ['didx'], bam: ['bai'] ]   // bam: [] would stop copying BAM indexes
  }
  ```
  A missing index is not an error. The copied indexes are listed after the staging message
  (`(also copying staged index files: ...)`).
- **Only the command itself is skipped.** Config, `$threads`/`$memory`, `uses`/`-n`/`-m`
  throttling, containers, conda and env settings are all still resolved. So a staged run still
  catches config errors, a missing `memory` entry for `$memory`, and similar problems. The copy
  always runs locally. Bpipe prints the resolved command it skipped:
  `Stage align using staged outputs from ... instead of executing: <command>`.
- **Commands with no outputs always run.** Checks (`check { exec ... }`) therefore run against
  the staged data, which makes staging a good way to test failure paths:

  ```
  test_data/
    baseline/  s1.align.bam  s1.align.bam.bai  s1.align.call.vcf   (real outputs from a small run)
    qc_fail/   s1.align.call.vcf                                     (crafted to fail the QC check)
  ```
  `--staged test_data/qc_fail,test_data/baseline` stages alignment and calling from `baseline`,
  overrides the VCF from `qc_fail`, and runs the QC check for real. Then assert on `test.out`, or
  on the check output, that the expected failure was reported.
- **Re-runs behave as you would want, with no `.bpipe` cleanup for staging changes.** Editing a
  staged file re-copies it. Outputs that came from a staged file are regenerated by any run that
  does not stage them from the same directory, so dummy data never leaks into a real run.
  Switching between scenario directories just works.
- `-t` shows what would be staged without creating anything:
  `(outputs would be copied from staged directory ...)` follows each staged command, followed by
  `(with index files: ...)` when there are any.
- **Limitations:** only outputs inside the run directory and on local storage can be staged.
  Staging is decided per *command*, so a
  stage with several `exec`s can have some commands staged and others run for real.
- **Wildcard outputs** (`produce('*.vcf')`, `produce(~'chr[0-9]+.vcf')`): *every* file in the
  matching staging directory that matches the wildcard is staged (first staging dir with any match
  wins). Existing local matches that aren't staged are kept (with a warning) and become outputs too,
  and staged copies are not removed by a later real run. So clear old outputs when switching
  between staged and real runs.
