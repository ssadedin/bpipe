# Getting Help

- Find help at: https://groups.google.com/forum/#!forum/bpipe-discuss
- Log issues on GitHub: https://github.com/ssadedin/bpipe/issues

Some common problems are listed below.

## Troubleshooting common problems

### My Pipeline Fails but I think it Succeeded!

Bpipe judges whether a pipeline stage succeeded by the exit code from the bash shell used to run the commands in the pipeline stage. Normally means that the success status comes from the last command that executes. Since Bpipe (v 0.9.8 onwards) runs bash with the "-e" flag, this means that any command (not just the last) can cause the failure.

Mostly this is OK, but some commands will return failure exit codes in innocuous situations. For example, 'grep' will return a failure exit status if it doesn't find what it is looking for. This is hardly a failure. You can prevent this by adding "|| true" to the end of your command. For example, to remove sex chromosomes from a BED file, we might do this:

```groovy 

    exec """
        cat $input.bed.bed | grep -v chrX | grep -v chrY > $output.bed || true
    """
```

### I can't get commands with \t, \r, \n, $1 or $! and other escaped characters to work

Since Bpipe is actually Groovy code, it unfortunately sees these as special characters that it interprets as part of the Groovy source code. To get them to go all the way through to your shell command, you need an extra backslash. For example, an Awk command can be converted as follows:

 {{{
  awk '{ print $1 "\t" $2 }' 
```

Should become: 

```groovy 

  awk '{ print \$1 "\\t" \$2 }'
```

Of course, it can get even more hairy if you need double escaping in the command itself - but is usually just a process of adding enough backslashes.

### Bpipe is confusing variables between my parallel pipeline stages

Bpipe is based on groovy which allows you to declare variables in a script without any prefix. For example, you can write something like this and it will appear to work just fine:
```groovy 

    SAMPLE_NAME=parse_sample_name(input.bam)
```

But there's a problem here: these variables are actually **global** variables. That means, if you run many pipeline stages at once, they will be all sharing the same value and overwriting each other's value. Hence you would find the variables being apparently confused between different parallel paths in your pipeline.

Fortunately there is a simple solution to this: you need to ad 'def' prior to your variables to make them local variables:
```groovy 

    def SAMPLE_NAME=parse_sample_name(input.bam)
```

### I get errors saying OutOfMemory when I run Bpipe!

Most pipelines don't need much memory, so to keep Bpipe lightweight, it runs with a Java memory limit of 256m. If, however, you run pipelines with a lot of input files, output files, heavy concurrency and/or intermediate stages, situations can occur where it needs a lot more memory. In these cases you can help the problem by setting the MAX_JAVA_MEM environment variable. You can set it permanently in your environment, but you can also set it on the Bpipe command line itself, by running Bpipe like this:
```groovy 

    MAX_JAVA_MEM=2g bpipe run ...
```

### My Job on a cluster is failing but there isn't enough information in the logs. How can I debug it?

Sometimes it can be tricky because the cluster queuing system may fail jobs outside of Bpipe's visibility (for example, you asked for more RAM than is available, etc.).  Debugging this is dependent on the particular resource manager in use. The PBS/Torque and SLURM managers create temporary job files that you can inspect and run yourself. You can find these in a location like this:

`.bpipe/commandtmp/<job id>/job.pbs`

So have a look at the files in .bpipe/commandtmp, find the most recent folder created, and the job should be in there. You can then submit this manually, for example with PBS/Torque:
```groovy 

qsub .bpipe/commandtmp/1680/job.pbs
```

And then check the status:
```groovy 

qstat -f <job id>
```

This can save you some time in working out why your job is not running.

### Bpipe keeps insisting an output I'm not even trying to create should exist

There can be a few different reasons for this, but a really common one is that you are making some reference to the `$output` variable that is misleading Bpipe into thinking you're going to produce the output. One of the fundamental mechanisms by which Bpipe works is that it uses your references to the `$output` and `$input` variables to infer what files are going into and out of your commands. For this reason you should try not to reference them other than in the context of a command that will produce them. Consider the following script:

```groovy 

copy_file = {
    println "I am going to copy to $output"
    produce(input + ".copy") {
        exec "cp $input $output"
    }
}
```

What Bpipe sees here is first, the reference to `$output` that is *inside the print statement*. So at that moment Bpipe resolves an appropriate output file name and sets the expectation that such an output will be produced by your pipeline stage. After that comes the produce - which Bpipe now sees as you stating your pipeline stage is going to create a *second* output as well. But of course, you are not trying to create two separate outputs, only one. So the result is that you will likely see an error at the end of the pipeline stage complaining that you didn't produce an output. The answer to this is to avoid making gratuitous references to the `$output` variable: use it just for representing outputs inside your commands.

### I can't get the output files to look how I want!

A common issue is that Bpipe's built in logic for transform and filter don't match what you want or expect for how the file names should come out. For example, some tools don't let you specify the output files and just make up their own based on some built in logic. Other times you have a mapping from input file to output file that is more complicated than a simple one to one (or one to many) that Bpipe allows for. And sometimes it is just a matter of personal preference for how the file names should look.

In all these cases you can use the fact that Bpipe is built on top of Groovy to write a tiny piece of logic that says how files should be transformed. You can then supply your calculated outputs explicitly to a `produce` statement to tell Bpipe exactly what you expect to be "produced".

For example, here we rename outputs according to our desire to include the machine Id and lane, with read end prefixed with 'R', with Picard's SamToFastq:
```groovy 

sam_to_fastq = {

    requires run_bar_code : "Id of sequencing machine. Used to name files to ensure uniqueness"

    // Inputs are SAM files with the lane indicated by the form <file name>*L<lane>.sam
    def outputs = [
        file(input.sam).name.replaceAll('*([+ run_bar_code + '_L$1_R1.fastq'),
        file(input.sam).name.replaceAll('_([1-2](1-2]).sam$','_')).sam$','_' + run_bar_code + '_L$1_R2.fastq')
    ]
    produce(outputs) {
        exec """
            java -Xmx2g -jar $PICARD_HOME/SamToFastq.jar
                    I=$input.sam
                    F=${output1}
                    F2=${output2}
        """
    }
}    
```

One noteworthy feature above is that rather than operating on the input file names directly, the `file(...)` function is used to create a File object from which the 'name' property is extracted. This allows you to operate on the file name without the path structure in front, in case the file is prefixed by relative or absolute directory structure.

### I have a command that doesn't output to a file - how can I make it not run every time?

Bpipe is very file driven; it's dependency mechanisms are all based on input files and output files and comparing them. Sometimes you will have a task that does something without creating a file. For example, sending an email, or uploading data to a remote database, etc. In these cases, since there's no output file, Bpipe by default doesn't think the command executed at all and re-runs it every time in your pipeline. The simple workaround for this is to create an output file yourself:
```groovy 

    copy_results = {
      exec """
        scp $input.bam myserver:/home/myaccount/results && date > $output.scp.txt
      """
    }
```

Here we use the date command to create a "dummy" file that Bpipe now considers to be an output. Notice that we use '&&' so that the file only gets created if the scp succeeds. It doesn't matter what you put in there, as long as you create some kind of file and reference it using $output, Bpipe's dependency management will work.
