
# The cleanup statement

### Synopsis
    
      cleanup <file pattern>, <file pattern>, ...

### Availability

      0.9.8.7+

### Behavior

The *cleanup* statement causes Bpipe to remove the files matching
the given patterns in such a way that they are not recreated if 
Bpipe is re-executed, unless they are required to produce an output
that is out of date. This behavior is identical to passing the
same files to the [bpipe cleanup](../Commands/cleanup.md) command.

### Examples

**Clean up intermediate BAM files produced**
```groovy 

   cleanup_bams = {
        cleanup "*.dedupe.bam", "*.realign.bam"
   }
```
