# The preserve command

## Synopsis

    
    
        bpipe preserve <file1> [<file2>]...
    

## Availability

0.9.8+

## Description

Causes the specified outputs to be marked as "preserved" in the output file meta data. Preserved files will not be deleted by the "cleanup" command. It is not necessary to mark outputs as "preserved" if they are final outputs ("leaf nodes" in the dependency graph) since these are automatically treated as preserved. 

## Example 1

**Preserve all BAM files from the realignment stage**
Here we assume that all the BAM files that are outputs of the 'realignment' stage have the suffix 'realigned.bam'.
```groovy 

bpipe preserve *.realigned.bam
```
