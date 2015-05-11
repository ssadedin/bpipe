# The forward instruction

### Synopsis

    
    
      forward <output1> <output2>...
    
      forward <input> to <output>
  

### Behavior

A forward instruction overrides the default files that are used for inputs to
the next pipeline stage with files that you specify explicitly. You can provide
a hard coded file name, but ideally you will pass files that are based on the
`input` or `output` implicit variables that Bpipe creates automatically so that
your pipeline stage remains generic. 

Bpipe uses heuristics to select the correct output from a pipeline stage that
would be passed forward by default to the next stage as an input (assuming the
next stage doesn't specify any constraints about what kind of input it
wants).  Sometimes however, the output from a pipeline stage is not
usually wanted by following stages or Bpipe's logic selects the wrong output.
In these cases it is useful to override the default with your own logic to
specify which output should become the default.

The forward instruction can also be used to create an alias (alternative name) for 
an output, by adding the `to <output>` construct. This allows a pipeline stage to
create an output under a desired name but forward an alternative name for 
consumption by the downstream pipeline. This can be used to facilitate compatibility
with downstream stages that are expecting a particular format in the name without
actually creating files with such names. It can also be used to create a "null" 
stage that renames an input as an output, for cases where an output under the 
expected name is required by the downstream pipeline.


### Examples

**Return a BAM file as next input instead of the index file created by the stage**
```groovy 

  index_bam = {
    transform(input.bam+'.bai') {
       exec """samtools index $input.bam"""
    }
    forward input.bam
  }
```

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
