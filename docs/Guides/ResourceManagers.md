# Using Bpipe with Cluster Resource Management Systems

In some environments commands cannot be issued directly but must be queued and run in a managed environment that controls how long they can run for and what resources (memory, CPU, storage space, etc) they can use.  Bpipe supports integration with third-party Resource Manager software to run commands that are part of your pipeline in this kind of environment.  Out of the box Bpipe supports  [Torque PBS](http://www.adaptivecomputing.com/products/torque.php), Sun / Oracle Grid Engine, and Platform LSF.  Others can be integrated if you implement a simple adapter shell script that can relay commands between Bpipe and the resource manager software.

## Using a Resource Manager

To make Bpipe use a Resource Manager to execute commands, you make a small file in the local directory (the same place as your pipeline script) called "bpipe.config".   In this script you can place a single line that names the resource manager - for example:
```groovy 

    executor="torque"
```

With this line you will get all Bpipe commands passed to the Torque queue (via the "qsub" program) using a default set of parameters and Bpipe will monitor the queue for you to pause until the job finishes and to retrieve the exit status from the command.   If a Torque job fails or times out then Bpipe will abort the pipeline just like it normally would and commands such as 'bpipe stop' will interface with Torque to cancel your jobs.  In short, everything will work just as if you were running the command normally with Bpipe.

*Note*: you can place 'global' configuration for executors in a file in your home directory called ".bpipeconfig".  These will be shared by all Bpipe pipelines in any directory, but any local configuration will override global configurations.

## Resource Manager Options

Although the above is very easy, it will unfortunately not work for all jobs because the default parameters that Bpipe uses will be either too large (causing your commands to wait a long time in the queue for resources or maybe even to never run at all) or too small (causing your commands to run but fail because they run out of time, do not have enough memory, storage space or violate other constraints).   The bpipe.config file allows you to adjust the options that are used for running your commands globally, on a per command basis or even based on the size or type of files involved. 

The following options are supported:

- `walltime` - how long the job is allowed to run.  See below for format details
- `procs` - number of processors
- `queue` - the queue to use (default = "batch")
- `memory` - amount of memory to reserve (GB).  Eg:  "4"
- `account` - the account under which to run the command

The walltime parameter can be specified in several different ways.  The most human friendly way to specify it is using the format "hh:mm:ss".  For example, to allow 3 hours and 30 minutes for a job, you can specify:
```groovy 

walltime="03:30:00"
```

Alternatively, you can also specify the walltime as a simple integer number of seconds, in which case the value 12600 would work instead:
```groovy 

walltime=12600
```

Finally, as a slightly more advanced option, you can specify the wall time as a n executable statement that returns the number of seconds.   The statement receives an array of [File](http://groovy.codehaus.org/groovy-jdk/java/io/File.html) objects that you can use to compute how large the walltime should be. For example:
```groovy 

walltime={ files -> files.size() ** 12600 }  // 3.5 hours for each file
```

To make Bpipe use to total size of all the files to determine the time you could use something like this:
```groovy 

walltime={ files -> files**.length().sum() / (1024*1024) * 60 } // 1 minute per MB
```

Note that in this case the expression will be evaluated for each command executed, so each command will get a different walltime.

## Different Options for Different Commands

So far as we have described things options would apply globally to every command Bpipe executes.  However obviously different commands will need different resources, so it is important to allow for that.  Bpipe lets you customize resources in two ways.  First, you can override the configuration based on the **command** that is executed.  For example, you could override the resources for the "bwa" command as follows:
```groovy 

walltime="03:30:00" // default is 3.5 hours
commands {
    bwa {
        walltime="07:00:00" // bwa will take twice as long
    }
}
```

Note that Bpipe matches the configuration by parsing the start of the command you execute.  This means that it only works for simple commands where the command happens to be the first token in the text passed to the 'exec' command.  So this would work:
```groovy 

    exec "bwa aln test.fastq > test.sai"
```

But this would not work:
```groovy 

    exec "time (bwa aln test.fastq > test.sai)"
```

For more complicated cases you can override the configuration using a completely arbitrary configuration name that you supply after the "exec" command itself.   So the second example could be made to work by modifying the "exec" portion:
```groovy 

    exec "time (bwa aln test.fastq > test.sai)","bwa"
```

## Sun Grid Engine options

In order to enable the Sun Grid Engine (SGE) resource manager define the following entry in the `bpipe.config` file :

```groovy 

    executor="sge"
```

The configuration options available are the same as described above, with the exception for the `account` option  which is not supported. 

The amount of memory to be reserved can be specified with the `memory` option. Values without any units is interpreted as number of bytes. Optionally it is possible to use `M` for mega-byte and `G` for giga-byte. Eg "100M" or "2G". 

### = Parallel environment =

To specify the parallel environment configuration, use the `procs` option providing the parallel environment name and the number of processes on which your parallel (MPI or OpenMP) application should run. For example: 
```groovy 

   executor="sge"
   procs="orte 8" 
```

### = Advanced configuration =

Bpipe submits jobs requests to the SGE resource manager through the `qsub` command. Experienced users may want/need to have fine control on the job submitting parameters. Using the option `sge_request_options`, it is possible to provide any configuration parameter accepted by the `qsub` command. For example:

```groovy 

   executor="sge"
   sge_request_options="-v VAR=value" 
```

The above example uses the `-v` parameter to define a variable named `VAR` in the target execution environment. Multiple parameters can be specified in the same option. Read more about the available parameters on the [qsub manual](http://gridscheduler.sourceforge.net/htmlman/htmlman1/qsub.html).

## Platform LSF

Pipeline execution thought the Platform LSF grid engine can be enabled specifying the string `lsf` as the executor in the `bpipe.config` file, as shown below: 
 
```groovy 

   executor="lsf"
```

The following options are supported by the LSF executor:

- `queue` - the queue to which the job(s) have to be submitted
- `jobname` - an optional name for the job
- `lsf_request_options` - any optional submission parameters accepted by the `bsub` command can be specified here.

For example: 

```groovy 

  executor="lsf" 
  queue="idle"
  lsf_request_options="-M 500000 -m 'hostA hostD hostB' -R 'rusage[swap=50]' "
```
