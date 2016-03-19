# The multi statement

### Synopsis

    
    
      multi <command>,<command>...
    
      multiExec <Groovy list of commands>
    
      multi <config>:<command>,
            <config>:<command>,
            ...
    
      multi <config>:[<command>,<command>,<command>,...],
            ...
 
### Availability

0.9.8+ 

### Behavior

The *multi* statement executes multiple commands in parallel and waits for them all to finish. If any of the commands fail the whole pipeline stage fails, and all the failures are reported.

Generally you will want to use Bpipe's built in
[parallelization](Language/ParallelTasks) features to run multiple commands in
parallel. However sometimes that may not fit how you want to model your
pipeline stages. The *multi* statement let's you perform small-scale
parallelization inside your pipeline stages.

*Note*: if you wish to pass a computed list of commands to *multi*, use the
        form *multiExec* instead (see example below).

### Examples

**Using comma delimited list of commands**

```groovy 

hello = {
  multi "echo mars > $output1.txt",
        "echo jupiter > $output2.txt",
        "echo earth > $output3.txt"
}
```

**Computing a list of commands**

```groovy 

hello = {  
        // Build a list of commands to execute in parallel
        def outputs = (1..3).collect { "out${it}.txt" }

        // Compute the commands we are going to execute
	int n = 0
        def commands =[$it > ${outputs[n++]("mars","jupiter","earth"].collect{"echo)}"} 

        // Tell Bpipe to produce the outputs from the commands
	produce(outputs) {
	    multiExec commands
	}
}

run { hello }
```
