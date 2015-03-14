#Bpipe command line usage 

Bpipe without any arguments will output a summary of the valid commands.

```
Bpipe Version 0.9.8.6   Built on Wed Oct 22 16:06:17 EST 2014

usage: bpipe [run|test|debug|execute] [options] <pipeline> <in1> <in2>...
             retry [test]
             stop
             history
             log
             jobs
             checks
             status
             cleanup
             query
             preserve
             diagram <pipeline> <in1> <in2>...
             diagrameditor <pipeline> <in1> <in2>...
 -b,--branch <arg>                Comma separated list of branches to
                                  limit execution to
 -d,--dir <arg>                   output directory
 -f,--filename <arg>              output file name of report
 -h,--help                        usage information
 -l,--resource <resource=value>   place limit on named resource
 -L,--interval <arg>              the default genomic interval to execute
                                  pipeline for (samtools format)
 -m,--memory <arg>                maximum memory
 -n,--threads <arg>               maximum threads
 -p,--param <param=value>         defines a pipeline parameter, or file of
                                  paramaters via @<file>
 -r,--report                      generate an HTML report / documentation
                                  for pipeline
 -R,--report <arg>                generate report using named template
 -t,--test                        test mode
 -u,--until <arg>                 run until stage given
 -v,--verbose                     print internal logging to standard error
 -y,--yes                         answer yes to any prompts or questions
```

Details on each argument are below.

---


## query

#### Synopsis

    
    
        bpipe cleanup [-y] [<file1>] [<file2>]...
    

#### Availability

0.9.8+

#### Description

Deletes intermediate files from outputs of a pipeline. primary purpose of this is to save space in storing unnecessary intermediate files that are not needed after a pipeline is finished running.

Intermediate files are files that are not final outputs ie. that are used as inputs in the pipeline for creating other outputs, and which have not been explicitly marked by the user as 'preserved' using the [preserve](preserve) command or annotation. Since these files are often not needed once the final result has been computed and are merely computational results that can be recreated from the inputs, it often makes sense to remove them. cleanup computes which files are intermediates and removes them for you automatically, while also marking that they were removed by cleanup in the file meta data so that the removal is logged, and  dependency calculations can understand that it is not necessary to recreate these files when running the pipeline again unless it is explicitly required.

With the `-y` option, the *cleanup* command will not ask the user for confirmation. Otherwise it will list the files to be deleted and give the user the option to proceed by deleting, moving to "trash" or cancelling.

#### Example 1

**Cleanup all intermediate files without prompting**
```groovy 

bpipe cleanup -y
```

#### Example 2

**Cleanup all Intermediate BAM files `**`.bam*
```groovy 

bpipe cleanup *.bam
```

---

## run

#### Synopsis

    
    
        bpipe execute [-h] [-t] [-d <output folder>] [-v] [-r] <pipeline> [<input 1>, <input 2>,...]
    

#### Options

`execute` command accepts the same options as the [run].

*Note*: If you want to run in test mode (to see what commands will be executed before running them), supply the -t option.

#### Description

Creates a Bpipe job for a pipeline defined on the command line and runs it.  This command causes the same behavior invoked by the `run` command, except that the pipeline is not defined in a file but rather on the command line itself. Since there is no way to define pipeline stages, all the stages used must be defined by automatically loaded pipeline stages that are present in files in the Bpipe lib directory (by default, `~/bpipes`, but you can set the `$BPIPE_LIB` environment variable to change it.

#### Example

Create a file called `stages.groovy` in `~/bpipes`, with the following contents:
```groovy 

hello = {
  exec 'echo hello'
}

world = {
  exec 'echo world'
}
```

Then execute:
```groovy 

  bpipe execute 'hello + world'
```

This behaves the same as creating a file, `test.groovy`:
```groovy 

  run { hello + world }
```

And running it using:
```groovy 

  bpipe run test.groovy
```

---


## history

#### Synopsis

    
    
        bpipe history
    

#### Description

Show a list of the jobs that were previously run in the local folder including the bpipe command line and arguments.


---

## jobs


#### Synopsis

    
    
        bpipe jobs
    

###### Description

Display a list of currently running Bpipe jobs.

---

## log

#### Synopsis

    
    
        bpipe log [options for tail]
    


#### Options

Internally the `log` command actually runs `tail` to display the log.  
You can pass any normal options you would like to the tail command, for example:
```groovy 

bpipe log -n 2000
```

Will display the last 2000 lines of the log instead of the default (which is to fill the screen according to the terminal height).

#### Description

Display the log file for the currently running, or most recently run Bpipe job in the local directory.  If the job is running, this command will "tail" the log file using the -f option so that you see a continuous scrolling log.  If it is not finished it will show the trailing lines of the log and exit back to the shell.

---

## preserve

#### Synopsis

    
    
        bpipe preserve <file1> [<file2>]...
    

#### Availability

0.9.8+

#### Description

Causes the specified outputs to be marked as "preserved" in the output file meta data. Preserved files will not be deleted by the "cleanup" command. It is not necessary to mark outputs as "preserved" if they are final outputs ("leaf nodes" in the dependency graph) since these are automatically treated as preserved. 

#### Example 1

**Preserve all BAM files from the realignment stage**
Here we assume that all the BAM files that are outputs of the 'realignment' stage have the suffix 'realigned.bam'.
```groovy 

bpipe preserve *.realigned.bam
```

---

## query

#### Synopsis

    
    
        bpipe query [<file1>] [<file2>]...
    

#### Availability

0.9.8+

#### Description

Displays information about outputs of a pipeline. With no arguments provided, query shows the dependency tree for the outputs in the current directory. This can be very large for a large number of outputs.

If specific files are provided as arguments, only the dependency tree for the specific files is shown, and in addition, detailed information about those files is displayed such as which command created the outputs, the direct inputs to the command, the date created and other meta data.

#### Example 1

**Display the whole dependency graph for all outputs in the current directory**
```groovy 

bpipe query
```

#### Example 2

**Query information about file foo.txt**
```groovy 

bpipe query foo.txt
```

---

## retry

#### Synopsis

    
    
        bpipe retry [test]
    

#### Description

Re-execute the most recently run Bpipe job.  This is typically how you can easily restart a job that may have failed or been interrupted from where it left off.

By adding *test* after the retry Bpipe will not actually execute the pipeline but instead show you the command that would be run if retry was used.

What retry actually does is rerun your whole pipeline from the start. However as it executes each pipeline stage Bpipe will check if the files expected to be created by the stage already exist and if so, and they are newer than all the inputs to the stage, it will skip executing the commands in the stage so that the pipeline moves rapidly until it reaches the first point that did not execute before.   Thus the key information that Bpipe uses to decide if a command within a pipeline stage should be re-executed is the file timestamps of the outputs of the command and the timestamps of the inputs to the command.

It should also be noted that when a command in a pipeline stage fails, Bpipe will 'clean up' the outputs of the command at the time of the failure.  Thus a pipeline stage that fails will not produce outputs and will be re-run when a retry is performed.

*Note*:  There is preliminary support for Bpipe also to recognize if the commands in the pipeline stage have been modified and to use that information when deciding whether they need to be re-executed.   This is done by saving the outputs created by each command executed in the local .bpipe folder so that when Bpipe sees outputs that are newer than inputs it will also then check if the command that created the outputs is the same as the new command to be executed.  This support is currently not enabled in the released version of Bpipe.

main feature of 'retry' is that it automatically looks up the exact that you ran the previous time you ran Bpipe in the local directory and re-executes it with the same options and input files. 

---


## run

#### Synopsis

    
    
        bpipe run [-h] [-t] [-d <output folder>] [-n <threads>] 
                  [-m <memory MB>] [-l <name>=<value>] [-v] [-r] 
                   <pipeline file> [<input 1>, <input 2>,...]
    

#### Options

<table>
  <tr><td>-h</td><td>Show help and exit</td></tr>
  <tr><td>-v</td><td>Display verbose / debug logging</td></tr>
  <tr><td>-r</td><td>Generate HTML report of run in `doc` directory</td></tr>
  <tr><td>-d</td><td>Generate outputs to folder instead of current directory</td></tr>
  <tr><td>-t</td><td>Run in test mode (see [test](test) command)</td></tr>
  <tr><td>-n</td><td>Limit concurrency to at most `n` simultaneous parallel branches</td></tr>
  <tr><td>-m</td><td>Limit memory usage to specified amount in MB (0.9.8+)</td></tr>
  <tr><td>-l</td><td>Specify a custom limit (0.9.8+)</td></tr>
  <tr><td>-p</td><td>Specify a parameter (variable) value</td></tr>
</table>

#### Description

Creates a Bpipe job for the pipeline defined in the specified file and runs it.  

The job runs in the background, detached from the current terminal (via nohup), but forwarding output to the terminal.

-n option limits concurrency that Bpipe itself initiates, however Bpipe will not prevent tasks that it launches from using concurrency themselves. So if yours themselves are spawning child processes or are multithreaded then you will need to account for that by supplying a smaller number to the -n option if you wish to have an absolute limit on processes or number of cores used.

The -m and -l options add limits that can be controlled by [uses](uses) blocks that are declared inside pipeline stages. Note that they don't impose any actual constraint on the memory used by tasks that run. They only control concurrency within [uses](uses) blocks that declare resources.

Often it is desirable to make pipelines customizable by exposing variables that the user running the pipeline can set externally. This can be achieved using the -p flag in the form -p `<name>=<value>`. Multiple -p flags can be provided to specify multiple parameters. Parameters may be read from a file with one value per line by specifying a argument starting with '@' followed by the file name. For example, 

```groovy 

bpipe run @params.txt pipeline.groovy
```

The file params.txt should have one option per line, for example:
```groovy 

-p foo=bar
-p baz=fubar
```

---

## status

#### Synopsis

    
    
        bpipe status
    

#### Description

Displays a list of currently running commands by the pipeline in the current directory.

---

## stop

#### Synopsis

    
    
        bpipe stop
    

#### Description

Stop the current job (if any) that is currently running in the local folder.  Any inputs not finished being created by the currently executing pipeline stage(s) will be cleaned up and moved to the [[Trash|Trash Folder]].

---

## test

#### Synopsis

    
    
        bpipe test [-h] [-t] [-d] [-v] <pipeline file> [<input 1>, <input 2>,...]
    

#### Description

Simulates execution of the pipeline until the first command is attempts to run.  Then aborts the pipeline, displaying the command to the console for inspection. 

Use the `test` command to debug your pipelines and check that long running commands are being correctly forumulated before they are executed.

Note: the `test` command can also be invoked using retry to test the command that will be executed if retry is performed:
```groovy 

bpipe retry test
```

---
