## JMS Integration

Bpipe supports some integration with JMS, currently through ActiveMQ 
and Amazon AQS SQS.

This support comes in the form of both inbound and outbound messages:

 - Pipelines can be configured to run on receipt of an inbound message
 - Messages can be sent by the [Send Command](/Language/Send) command.

## The Bpipe Agent

To run pipelines in response to messages, configure a Bpipe Agent.

The agent runs permanently in the background on a system and listens to the configured queue. The
agent is configured through the usual `bpipe.config` file, with a special `agent` section.
For example for ActiveMQ:

```
agent {
    commandQueue='run_pipeline_queue'
    responseQueue='bpipe_results'
    brokerURL='tcp://activemq.server.com:61616'
}
```

Once you have set up the configuration, you can start the agent in the local directory like
so:

```
bpipe agent -v -n 1
```

The above arguments turn on verbose mode (`-v`) and limit the number of messages to process at once to 
1 (`-n 1`).

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
        ],
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
            "test.txt",
            "test2.txt"
        ] ,
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

## Monitoring Status - Ping Message

The Bpipe agent recognises a special message where the body is composed only of the word `ping`. When this message
is received, it will respond by sending a message to the queue specified in the `JMSReplyTo` or `reply-to` header.
This message will have a JSON body containing information about the agent, and receiving it can be used to confirm
that the agent is alive and processing messages.

## Using Response Messages

If you are integrating Bpipe into a larger system, it is likely that you will want to know
the status of the pipeline that run (did it succeed, fail, where is it up to, etc).

To support these use cases, the Bpipe Agent will send messages to a "response queue". This can
be set to a fixed value in the agent configuration:

```groovy
agent {
    commandQueue='run_pipeline_queue'
    responseQueue='bpipe_results' // send output to JMS queue called bpipe_results
    brokerURL='tcp://activemq.server.com:61616'
}
```

Alternatively, Bpipe will respect the JMS Reply-To header ("reply-to" or "JMSReplyTo") so 
another strategy is to leave this blank and configure the reply queue on the appropriate 
messages. This latter strategy has the advantage that you can then use different
response queues for different messages.

**Reply Modes**

The Bpipe Agent supports several "reply modes" which contain different amounts of detail.
To set these, configure the `outputMode` setting in the agent `bpipe.config` file:

```groovy
agent {
    commandQueue='run_pipeline_queue'
    responseQueue='bpipe_results' 
    brokerURL='tcp://activemq.server.com:61616'
    outputMode="stream"

}
```

The `outputMode` can be set to the following values:

- none  : do not send anything
- stream : send full pipeline output, streamed in chunks
- reply : send a single message at the end, containing status
- both : send both streaming output and a final reply message


## Handling Pipeline Completion 

It can be useful to coordinate downstream actions when the pipeline completes running. For this purpose, Bpipe will observe the `reply-to` or `JMSReplyTo`
property of messages. When a pipeline initiated by the agent completes, if 
one of these properties is set, Bpipe will send a message to the corresponding
queue as a reply. In such a message, if a correlation id is set, then the message
will have the same correlation id.

This capability is designed to interoperate with frameworks such as 
[Apache Camel](https://camel.apache.org/) which can route messages through
predefined workflows using this system. For example, a Camel route could be
defined using the Groovy DSL to run a pipeline in response to a message
and then process the results:

```groovy
from('activemq:analyse_file')
.transform { e, c ->
	groovy.json.JsonOutput.toJson(
		"command" : "run",
		"arguments": [
			"pipeline/batch.groovy",
            e.in.body // the file to analyse
		] + 
		"directory": "/some/path/on/your/system"
	)
}
.inOut()
    .to('activemq:run_bpipe?requestTimeout=720000') // 2 hour timeout
.inOnly()
    .process { e ->
        println "The results from the pipeline were: $e.body.in"
    }
```

Note that the `inOut` automatically handles the correlation id and reply-to headers and waits for the reply. The bpipe agent, in this case, would be configured to 
listen on the `run_bpipe` queue.

## Configuring Security

You can cause Bpipe to authenticate using a username and password when creating the connection
by adding these properties to the configuration:

```
agent {
    commandQueue='run_pipeline_queue'
    responseQueue='bpipe_results'
    brokerURL='tcp://activemq.server.com:61616'
    username='myuser'
    passsword='secretpassword'
}
```

If you prefer not to hard code the password into your configuration, you can use regular Groovy 
language features to resolve it an alternative way. For example, to read it from an environment 
variable:


```
agent {
    ....
    passsword=System.getenv('ACTIVEMQ_PASSWORD')
}
```

The same configuration properties are applicable when configuring ActiveMQ as a notification 
channel.

## AWS SQS

SQS is an AWS hosted queuing service that enables smooth cloud integration with messaging
services. Bpipe supports notifications and the Bpipe agent connection through SQS.

To configure notifications to SQS, add an `AWSSQS` section to the notifications configuration
in the `bpipe.config` file:

```groovy
notifications {
    AWSSQS {
        queue='my-test-queue'
        region='ap-southeast-2'
        accessKey = "..."
        accessSecret = "..."
        events=''
    }
}
```

Similarly, to run the agent using SQS, specify the `type` as `sqs` in the `agent` configuration
block:

```groovy
agent {
    type='sqs'
    commandQueue='my-test-queue'
    region='ap-southeast-2'
    accessKey = "..."
    accessSecret = "..."
}
```

**Note**:  for SQS integration, an AWS profile name can be specified instead of directly including
the key and secret. This will cause Bpipe to attempt to read the user's `~/.aws/credentials` file
to locate the corresponding profile. To do this, specify a `profile` attribute instead of the
`accessKey` and `accessSecret` details.

Example:

```groovy
agent {
    type='sqs'
    commandQueue='my-test-queue'
    region='ap-southeast-2'
    profile='default'
}
```

