# The doc statement

### Synopsis

    
    
      doc [title] | ( attribute1 : value, attribute2: value...)
    
  
### Attributes

Valid documentation Attributes are:

<table>
  <tr><td>title</td><td>Brief one line description of the pipeline stage</td></tr>
  <tr><td>desc</td><td>Longer description of a pipeline stage</td></tr>
  <tr><td>author</td><td>Name or email address of the author of the pipeline stage</td></tr>
  <tr><td>constraints</td><td>Any warnings or constraints about using the pipeline stage</td></tr>
</table>

### Behavior

A *doc* statement adds documentation to a pipeline stage.  It is only valid within a declaration of a pipeline stage.  The *doc* statement has one of two forms - a brief from that allows you to give a simple one line description of a pipeline stage and a longer form that lets you specify multiple attributes.

Documentation added with a *doc* statement is currently used when you generate a HTML report for a run (see [run](Commands/run) command).

*Note*: Bpipe will augment documentation provided with the `doc` command with additional values that are determined at run time, such as the inputs of the pipeline stage, the outputs of the pipeline stage, the versions of executable tools used in the pipeline stage (where those have been able to be determined) and the execution time details (start, stop, duration).

### Examples

**Add a title for a pipeline stage**
```groovy 

  align_with_bwa = {
      doc "Align FastQ files using BWA"
  }
```

**Add full documentation for a pipeline stage**
```groovy 

  align_with_bwa = {
      doc title: "Align FastQ files using BWA",
          desc:  """Performs alignment using BWA on Illumina FASTQ 
                    input files, producing output BAM files.  The BAM 
                    file is not indexed, so you may need to follow this
                    stage with an "index_bam" stage.""",
          constraints: "Only works with Illumina 1.9+ format.",
          author: "bioinformaticist@bpipe.org"
  }
```
