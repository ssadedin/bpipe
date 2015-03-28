# The cleanup command

## Synopsis

    bpipe cleanup [-y] [<file1>] [<file2>]...
    
## Availability

0.9.8+

## Description

Deletes intermediate files from outputs of a pipeline. The primary purpose of
this command is to save space in storing unnecessary intermediate files that
are not needed after a pipeline is finished running.

Intermediate files are files that are not final outputs ie. that are used as
inputs in the pipeline for creating other outputs, and which have not been
explicitly marked by the user as 'preserved' using the
[preserve](Commands/preserve) command or annotation. Since these files are
often not needed once the final result has been computed and are merely
computational results that can be recreated from the inputs, it often makes
sense to remove them. The cleanup command computes which files are
intermediates and removes them for you automatically, while also marking that
they were removed by cleanup in the file meta data so that the removal is
logged, and  dependency calculations can understand that it is not necessary to
recreate these files when running the pipeline again unless it is explicitly
required.

With the `-y` option, the *cleanup* command will not ask the user for confirmation. Otherwise it will list the files to be deleted and give the user the option to proceed by deleting, moving to "trash" or cancelling.

## Example 1

**Cleanup all intermediate files without prompting**
```groovy 

bpipe cleanup -y
```

## Example 2

**Cleanup all Intermediate BAM files `**`.bam*
```groovy 

bpipe cleanup *.bam
```
