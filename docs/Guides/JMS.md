# JMS Integration

Bpipe supports some integration with JMS, currently through ActiveMQ.

This support comes in the form of both inbound and outbound messages:

 - Pipelines can be configured to run on receipt of an inbound message
 - Messages can be sent by the [Send Command](../Language/Send/) command.

## The Bpipe Agent

To run pipelines in response to messages, configure a Bpipe Agent.

The agent runs permanently in the background on a system and listens to the configured queue. The
agent is configured through the usual `bpipe.config` file, with a special `agent` section:

```
agent {
    commandQueue='run_pipeline_queue'
    responseQueue='bpipe_results'
    brokerURL='tcp://activemq.server.com:61616'
}
```

The agent expects messages to arrive in a specific format containing instructions to define
the bpipe command to execute. The format is a JSON payload with the following elements:

```json
    {
        "id": 0,
        "command": <bpipe command>
        "arguments": [
            <argument 1>
            <argument 2>,
            ....
        ] 
        "directory": <directory to run command in>
    }
```

The id in the command is for your own reference and can be hard coded to 0 if you do not need it.

An example of a run command could look like this:

```json
    {
        "id": 0,
        "command": "run",
        "arguments": [
            "test.groovy", 
            "test.txt"
            "test2.txt"
        ] 
        "directory": "/home/user/test_pipelines"
    }
```

## Transforming Inbound Messages 

If you need to have Bpipe respond to messages that are not in this format, you
can transform the message with an adapter that is defined in terms of a
[Closure](http://groovy-lang.org/closures.html) (or function) that does the
transformation, using the `transform` configuration attribute inside the
`agent` configuration section . For example:

```
    transform = { message ->
        [
            id: 0,
            command: "run",
            arguments: [
                "-p",
                "sample=$message.sample",
                "-p",
                "batch=$message.batch",
                "-p",
                "run_id=$message.run_id",
                "process_sample.groovy",
                message.sample
            ],

            directory: '/usr/local/batch_processing'
        ]
    }
```

Note that the message payload is still expected to be JSON and is pre-parsed into
a Java object structure (Map, List, etc). The transform needs to produce a 
Map / List structure identical to the JSON format of the standard message, 
however it is defined using Groovy code.
