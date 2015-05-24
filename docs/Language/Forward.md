# The forward instruction

### Synopsis

    
    
      forward <output1>,<output2>...

      forward([<output1>, <output2>...])
  

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

