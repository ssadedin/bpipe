# Directory Convenience Function

Sometimes a command you want requires not the name of an output file, but the name of the directory in which that file resides. This can cause a bit of confusion to Bpipe, because the real output is the file, but what you reference in your command is the directory.

To help with this, Bpipe offers a special meaning for the "dir" extension. When you reference `$output.dir` Bpipe treats it as a reference to the output **file**, but it passes the directory in which the file resides to the command that is executing.

The `$input.dir` variable also has special meaning. In this case the `dir` is taken to mean that the whole directory itself should be considered an input, and Bpipe will search for an input that is in fact a directory, rather than a file. This enables you to use the file-extension metaphor for selecting directories within your pipeline for input to your commands. 

### Setting the Output Directory

While the `input.dir` is a fixed value, `output.dir` is a writeable value, so you can use it to change the value of directory to which outputs will go. Bpipe currently expects all outputs from a pipeline stage to go to the same directory, so setting this to multiple values or different values will not work.

*Note*: if you modify `output.dir`, be mindful that it must execute before the expression defining the command you wish to execute is evaluated. For example, the following will not work, because output.dir is changed after the output.txt was already evaluated.

```groovy 

hello = {
   def myOutput = output.txt
   exec "cp $input.csv $myOutput"
   output.dir="out_dir"
}
```

### Example

Here we wish to have fastqc put its output zip file into a directory called "qc_data". However fastqc won't let us tell it the full path of the zip file, only the directory name.
```groovy 

fastqc = {
    // Start by telling Bpipe where the outputs are going to go
    output.dir = "qc_data"

    // Then tell Bpipe how the file name is getting transformed
    // Notice we don't need any reference to the output directory here!
    transform(".fastq.gz") to ("_fastqc.zip") {
        // Now in our command, Bpipe gives us the folder as the value,
        // while knowing that it is referencing the zip file output 
        // specified in our transform
        exec "mkdir -p $output.dir; fastqc -o $output.dir $input.gz" 
    }
}
```
