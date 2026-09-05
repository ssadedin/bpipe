## The concurrency annotation

### Synopsis

    
    
    @concurrency(<max instances>)
    <stage_name> = {
        < statements of the pipeline stage >
    }
    
    
    concurrency(<max instances>) {
        < statements to limit the concurrency of >
    }
    

### Availability

0.9.15 and higher

### Behavior

The *concurrency* annotation restricts the number of instances of a pipeline
stage that are allowed to execute at the same time. No matter how much
parallelism the rest of the pipeline generates, at most the given number of
instances of the annotated stage will run, and any further instances wait until
one of the running ones finishes.

The annotation is applied to a stage declaration, and it limits that stage
wherever it appears in a pipeline - including when the same stage is used more
than once, or in more than one branch. Every instance of the stage shares a
single set of "slots", which are handed out in the order the instances asked for
them, so no branch can be starved by others.

The block form does the same thing for the statements inside the block, rather
than for an entire stage.

This limit is applied *in addition* to the overall concurrency of the pipeline,
which is set with the `-n` option (see [uses](/Language/uses) for limiting
concurrency according to the resources a stage consumes). So the number of
instances of a stage that actually run at once is the lesser of the number given
here and the `-n` value.

### Examples

**Ensure only four instances of an alignment run at once, even though the
pipeline is run with a much higher overall concurrency**

```groovy

@concurrency(4)
align_with_bwa = {
    exec """
        bwa mem -t 4 $REF $input.fq > $output.sai
    """
}

run {
    ["sample1","sample2","sample3", ... ] * [ align_with_bwa ]
}
```

**Limit the number of stages calling a service that can't handle many
simultaneous requests**

```groovy

@concurrency(1)
annotate_with_service = {
    exec "curl -d $input.json https://my.server/annotate > $output.json"
}
```

**Apply the limit to just part of a stage**

```groovy

process = {
    exec "convert $input.png $output.tif"

    concurrency(1) {
        exec "upload_to_server $output.tif"
    }
}
```

### Notes

  * The limit is held inside the process running the pipeline. It applies to all
    the stages it runs, whether those run locally, on a cluster, or in the
    cloud, since those are all launched from the one process. But it does not
    coordinate between separate `bpipe run` invocations: if you start the same
    pipeline twice, each will allow its own maximum number of instances.

  * A stage can only carry one annotation, so the following will not work:

    ```groovy
    @concurrency(2)
    @transform("bam")
    align = { ... }
    ```

    Instead, use the annotation for the concurrency and write the other one as a
    statement inside the stage body:

    ```groovy
    @concurrency(2)
    align = {
        transform("bam") {
            ...
        }
    }
    ```

  * Annotated instances wait for a slot *before* running any of their commands,
    and hold it until the stage body finishes. So a stage waiting for a slot
    still occupies one of the threads of the pipeline, and setting a very low
    limit on a stage near the start of a highly parallel pipeline can reduce the
    parallelism available to the rest of it.

  * If a stage that is limited to a small number of instances contains a nested
    run of that same stage, the inner instances will wait for slots held by
    their own ancestors, and the pipeline will deadlock. Bpipe cannot detect
    this, so avoid nesting a limited stage inside itself.

### See Also

  * [uses](/Language/uses) - limits concurrency according to the resources
    (threads, memory, custom resources) a stage consumes
  * [Config](/Language/Config) - the `limits` configuration entry sets the
    maximum of each named resource
