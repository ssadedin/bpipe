# The using instruction

### Synopsis

    
    
      <pipeline stage>.using(<variable>:<value>, <variable2>:value...)
    
  

### Behavior

A *using* instruction causes variables to be defined inside a pipeline stage as part of pipeline construction.  It is only valid inside a Bpipe *run* or [segment](Language/Segments) clause.  The *using* instruction is useful to pass parameters or configuration attributes to pipeline stages to allow them to work differently for different pipelines.

### Examples

**Sleep for a configurable amount of time and say hello to a specified user**
```groovy 

  hello = {
    exec "sleep $time"
    exec """echo "hello $name" """
  }

  run {
    hello.using(time: 10, name: "world") + hello.using(time:5, name:"mars")
  }
```
