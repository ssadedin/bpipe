# Advanced Settings and Variables

## Introduction

Bpipe supports a number of advanced settings that can be used to solve problems
in particular environments. This page is a catch all for miscellaneous settings that
are not found in other sections.

## Settings

### Output Scan Concurrency

If your pipeline produces very many input or output files, you may find that
it pauses for a long time at particular points. This is because Bpipe needs
to verify that every single one of those files exists. Just doing that check can take
a long time if file system calls have a lot of latency - as they can on some
file systems such as remote mounted NFS partitions. To improve performance you can have
Bpipe execute file scans in parallel. To enable this, set the `outputScanConcurrency` 
parameter in your `bpipe.config` file. eg:

```
outputScanConcurrency=10
```

This will cause Bpipe to use up to 10 threads in parallel to scan the file system. Do not raise 
this value too high on systems where allocation of file handles is restricted, because each thread
consumes a file handle of its own.

### Job Launch Separation

By default Bpipe will launch jobs with almost no separation in time. That is, if you 
have 3000 commands that can run concurrently in a scheduling system, Bpipe may 
try to submit 3000 jobs in the space of a few milliseconds. This can create a spike
in load which is not very friendly to the queuing system, and in some cases it can even
result in failures if the system becomes overloaded as it tries to digest so many new jobs.
It can help in these cases to introduce a delay in between the launch of each job. To do this,
add the following setting to your .bpipeconfig or bpipe.config file:

```
jobLaunchSeparationMs=3000
```

The above example would space every command by at least 3 seconds - if you really have 3000 to 
jobs to launch this will make your whole pipeline take 9000 seconds to get started, so you will
need to balance the value of this setting against the capabilities and robustness of the
system the jobs are running on.

### Post Command Hook

If you want to run something every single time after each command finishes, you can set it as 
a "post command" using the `post_cmd` configuration. For example, to print the date and time when
every command completes, in `bpipe.config`, you can put:

```
post_cmd="""
echo "Command finished at: `date`"
"""
```

*Note*: this is not supported by every executor, but is supported by most, including the local executor.

### Special Variables

Sometimes it is helpful for your pipeline script to know variables about its environment. The following
table defines variables that are available to pipeline scripts:

| Variable | Meaning |
|----------|---------|
| bpipe.Config.scriptDirectory | The directory in which the currently running pipeline script is situated |



