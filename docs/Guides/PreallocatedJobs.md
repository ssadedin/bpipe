# Preallocating Jobs

In some systems, jobs must be submitted significantly ahead of when they are
expected to run. In such environments, Bpipe pipelines can perform suboptimally because
each job is submitted "just in time" for execution, and may suffer a long wait time
in the queue before it is executed. Bpipe offers a workaround for these environments
in the form of "pre-allocated" jobs.

## Configuring Preallocated Jobs

Preallocated jobs are configured in the `bpipe.config` file. They are configured in 
a section called `preallocate`. Rather than configuring a preallocated job for each
command in the pipeline, preallocated jobs are configured in "pools". Each pool is 
given a name. An example of a preallocation section is as follows:

```
preallocate {
    small {
        jobs=4
        walltime="8:00:00"
        memory="16g"
        configs=["berry","juice"]
    }
}
```

In this example, the name of the preallocation pool is "small". The pool contains four 
jobs that all request 16GB of RAM and 8 hours of run time. These jobs will run any 
command with the configuration names of "berry" or "juice".

**Note**: In the current implementation Bpipe will not check that "berry" and "juice" 
have configurations that are compatible with with the requirements of the preallocation. 
That is, if "berry" commands actually need 32G of memory then they will be run using a 
job that requests 16G of memory and likely fail. 


If no configs entry is provided, Bpipe assumes that the configs entry is the same as 
the name of the preallocation pool.

## Persistent Preallocated Jobs

If you have multiple Bpipe pipelines to run, you can start a job pool that is shared
between separate runs of Bpipe. This prevents you having to wait for a job to become
available when a pipeline first starts.

### Configuration

To enable persistent jobs, the job pool should be marked as persistent:

```
preallocate {
    small {
        jobs=4
        walltime="8:00:00"
        memory="16g"
        configs=["berry","juice"]
        persist=true
    }
}
```

In this case, the pool of 4 jobs will not be shut down at the end of pipeline execution.
Instead, they will remain running but idle, and will be available for use by another
Bpipe pipeline.

### Refresh Frequency for Persistent Job Pools

Persistent jobs will run down the time on their "clock" while they sit in the pool. Therefore,
it is necessary to stop and refresh jobs periodicially to ensure that the jobs in the pool
actually have enough time to execute the tasks they are designed to carry out. Bpipe calculates
the ratio of the time the job has remaining to the total walltime allocated and triggers a
refresh of a job in the pool when the threshold is breached. By default, this threshold is 0.2 - 
that is, a when a persistent preallocated job reaches 20% of its walltime, a new job will be 
requested and the old one will be retired. You can set this threshold by setting the `timeFractionThreshold`
parameter in the configuration. Eg:

```

preallocate {
    small {
        jobs=4
        walltime="8:00:00"
        memory="16g"
        configs=["berry","juice"]
        timeFractionThreshold=0.4
        persist=true
    }
}
```

### Stopping persistent Job Pools

Since they are not stopped when your pipeline ends, you need to manually shut down
persistent jobs (unless they have ended naturally by exceeding their time limit).

To shut down persistent jobs, add the "all" argument to `bpipe stop`:

```bash
bpipe stop preallocated
```

### Starting a Persistent Job Pool in Advance

You can start a persistent job without running the pipeline at all by using the
`preallocate` command:

```
bpipe preallocate
```





