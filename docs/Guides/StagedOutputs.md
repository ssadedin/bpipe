# Staged Outputs

When developing or testing a pipeline it is often useful to skip steps that
take a long time or a lot of compute, or to present a downstream stage with a
particular kind of data (for example, a sample that fails QC) without needing
real end-to-end data that produces it. Bpipe supports this with *staged
outputs*: a directory of files that are copied into place instead of running
the commands that would create them.

## Usage

```bash
bpipe run --staged <dir>[,<dir>,...] pipeline.groovy <inputs...>
```

The `--staged` option is accepted by every command that runs a pipeline,
including [execute](../Commands/execute.md), [test](../Commands/test.md) and
[retry](../Commands/retry.md). Staging directories can also be set in
`bpipe.config`:

```groovy
staged = ['test_data/qc_fail']
```

## How it works

A staging directory mirrors the directory structure that the pipeline sees from
the directory it runs in. When Bpipe is about to execute a command, it looks
up each of the command's outputs, by its path relative to the run directory,
in each staging directory in turn:

* if *all* of the command's outputs are found, the command is not executed.
  Instead the staged files are copied into place and the command that would have
  run is printed, eg:

        Stage align in branch s1 using staged outputs from /data/test/staged instead of executing:

                bwa mem -t 4 ref.fa s1.fastq | samtools view -bS - > s1.align.bam

* if *some* of the command's outputs are found, a warning is printed and the
  command runs normally, since a mix of staged and real outputs from one command
  is unlikely to be consistent
* if none are found the command runs normally

So for a pipeline run in `/work/run1` that would create `/work/run1/align/s1.bam`,
the staged file would be `<staging dir>/align/s1.bam`. A directory output is
satisfied by a staged directory, whose contents are copied.

When more than one staging directory is given, the first one containing a file
wins. This lets you layer a small directory of special cases (eg: a QC failure)
over a baseline set of staged files:

```bash
bpipe run --staged test_data/qc_fail,test_data/baseline pipeline.groovy s1.fastq
```

Commands that have no outputs (for example, most [checks](../Language/Check.md))
are always executed, so they run against the staged data. This is what makes
staging useful for testing how a pipeline reacts to particular data.

### What is still exercised

Only the execution of the command itself is skipped. Everything else Bpipe does
to prepare a command happens as normal: configuration is resolved, resources
are allocated (including waiting for them under `-n`, `-m` and `uses`),
`$threads` and `$memory` are assigned, and environment, conda/uv and container
wrapping are applied. Errors in any of these surface just as they would in a
real run, and the printed command is the fully resolved one. The copy itself is
always run locally, even if the command is configured for a cluster or cloud
executor.

## Staged outputs in later runs

Outputs created from staged files are recorded as such in Bpipe's output meta
data. A later run that does not stage them from the same directory treats them as
out of date and runs the real commands, so dummy data never silently flows into a
real run. Similarly, with staging enabled an output is re-copied if its staged
file is modified, or if the output was previously created by running the real command.

## Limitations

* only outputs inside the run directory, on local storage, can be staged
* outputs specified as globs (eg: `produce("*.vcf")`) cannot be staged
* staging is decided per command: a stage that runs several commands can have
  some of them staged and others executed
