# The glob function

### Synopsis

    
    
      glob(<pattern>)
    

### Availability

0.9.8_beta_1 and higher

### Behavior

Returns a list of all the files on the file system that match the specified wildcard pattern. The pattern uses the same format as shell wildcard syntax.

The *glob* function is useful if you want to match a set of files as inputs to a pipeline stage and you are not able to achieve it using the normal Bpipe input variables. For example, in situations where you want to match a complicated set of files, or files that are drawn from across different pipeline branches that don't feed their inputs to each other. A call to *glob* can be used inside a [from](Language/From) statement to feed the matched files  into the input variables so that they can be referenced in [exec](Language/Exec) and other statements in pipeline stages.

*Note*: in general, it is better to use `from` directly using a wild card pattern if you can. Such a form searches backwards through the outputs of previous stages rather than directly on the file system. This is much more robust than just scanning the file system for files matching the pattern.

### Examples

**Run a command with all CSV files in the local directory as input**
```groovy 

  process_csv = {
    from(glob("**.csv")) {
        exec "convert_to_xml.py $inputs > $output"
    }
  }
```

In this example, the *$input* variable contains all files matching the pattern *`**`.csv* from the local directory, including the original inputs, and intermediate outputs of the pipeline.
