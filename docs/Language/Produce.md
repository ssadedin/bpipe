# The Produce Statement

    
    
        produce(<outputs>|[output1,output2,...]) {
          < statements that produce outputs >
        }
    

### Behavior

The *produce* statement declares a block of statements that will be executed *transactionally* to create a given set of outputs.  In this context, "transactional" means that all the statements either succeed together or fail together so that outputs are either fully created, or none are created (in reality, some outputs may be created but Bpipe will move such outputs to the [[Trash|trash folder]]).  Although you do not need to use *produce* to use Bpipe, using *produce* adds robustness and clarity to your Bpipe scripts by making explicit the outputs that are created from a particular pipeline stage. This causes the following behavior:

- If a statement in the enclosed block fails or is interrupted, the specified outputs will be "cleaned up", ie. moved to the [[Trash|trash folder]]
- The implicit variable 'output' will be defined as the specified value(s), allowing it to be used in the enclosing block
- The specified output will become the default input to the next stage in the pipeline
- If the specified output already exists and is newer than all the input files then the produce block will not be executed.

*Note*:  in most cases it will make more sense to use the convenience wrappers [filter](Language/Filter) or [transform](Language/Transform) rather than using *produce* directly as these statements will automatically determine an appropriate file name for the output based on the file name of the input.


A wildcard pattern can also be provided as input to `produce`.  In such a case, the `$output` variable is not assigned a value, but after the `produce` block executes, the file system is scanned for files matching the wild card pattern and any files found that were not present before running the command are treated as outputs of the block.

*Note*: as Bpipe assumes ALL matching new files are outputs from the `produce` block, using a wild card pattern inside a parallel block should be treated with caution, as multiple executing pathways may "see" each other's files.

### Examples

**Produce a single output**
```groovy 

produce("foo.txt") {
  exec "echo 'Hello World' > $output"
}
```

**Produce multiple outputs**
```groovy 

produce("foo.txt", "bar.txt") {
  exec "echo Hello > $output1"
  exec "echo World > $output2"
}
```
