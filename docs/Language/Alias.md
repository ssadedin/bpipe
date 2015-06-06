# The forward instruction

### Synopsis

      alias <input> to <output>
  
### Behavior

The alias instruction can is used to create an alternative name for 
an output, by adding the `to <output>` construct. This allows a pipeline stage to
create an output under a desired name but forward an alternative name for 
consumption by the downstream pipeline. This can be used to facilitate compatibility
with downstream stages that are expecting a particular format in the name without
actually creating files with such names. It can also be used to create a "null" 
stage that renames an input as an output, for cases where an output under the 
expected name is required by the downstream pipeline.

### Examples

**Override a Downstream Stage with a Null Implementation by Aliasing the Input File to the Output Name**
```groovy

  // The main deduplication stage
  dedupe = {
    exec """
        java -Xmx4g -Djava.io.tmpdir=$TMPDIR -jar /usr/local/picard-tools/lib/MarkDuplicates.jar
             INPUT=$input.bam 
             REMOVE_DUPLICATES=true 
             VALIDATION_STRINGENCY=LENIENT 
             AS=true 
             METRICS_FILE=$output.metrics
             OUTPUT=$output.bam
    """
  }
  
  configure = {
      var dedupe_disabled : false
      if(dedupe_disabled) {
          branch.dedupe = {
              println "Deduplication is disabled in branch $branch.name"
              forward input.bam to output.bam
          }
      }
  }


  run { 
      configure + dedupe
  }
```
