# Hooking into Pipeline Events

More advanced users of Bpipe may wish to write more general handlers for
events that occur during the pipeline execution. Internally, many functions of
Bpipe are written using an "event handling" framework. This framework is also
accessible to pipeline scripts themselves. This section gives some brief
notes about how these features can be accessed from pipeline scripts.

## Listening to Pipeline Events

Here is an example of a very basic listener that receives a notification
each time a pipeline stage completes:

```groovy 

bpipe.EventManager.getInstance().addListener(bpipe.PipelineEvent.STAGE_COMPLETED) { type, desc, details ->
    println "An event occurred! Stage: $details.stage.stageName, Event Type: $type, Description: $desc"
}
```

The addListener function can be called as many times as you  like:

```groovy

import static bpipe.PipelineEvent.*

[STAGE_STARTED, STAGE_COMPLETED].each { event ->

    bpipe.EventManager.getInstance().addListener(event) { type, desc, details ->
        println "An event occurred! Stage: $details.stage.stageName, Event Type: $type, Description: $desc"
    }
}
```

Note that here we imported the event classes so that they can be referred to
without the prefix `bpipe.PipelineEvent`.

## Available Events

To see which events are available, have a look at the code for the PipelineEvent class:

https://github.com/ssadedin/bpipe/blob/master/src/main/groovy/bpipe/PipelineEvent.java

## Details Object

With each event notification, a custom "details" object is provided as the third argument.
The contents of this object are specific to each event, and in some cases could even 
vary for a single event (for example, some attributes might only be available in the
STAGE_COMPMLETED event if the stage succeeded).

An easy way to see what is avaialble is to dump them out:

```
import static bpipe.PipelineEvent.*
bpipe.EventManager.getInstance().addListener(STAGE_COMPLETED) { type, desc, details ->
    println "Event occurred! Here are the details:\n " + details.collect { key, value -> "$key : $value\n" }.join('') 
}
```

Take care not to modify the values of any internal Bpipe objects, as the behavior of
Bpipe may be undefined if you do. 

