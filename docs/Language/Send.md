# The send statement

### Synopsis

    
    
    send [text {<text>} | html { <html> } | report(<template>) | json(<data>)] to <notification channel name><pre>
    send [text {<text>} | html { <html> } | report(<template>) | json(<data>)] to channel:<channel name>, 
                                                               subject:<subject>, 
                                                               file: <file to attach> 
 
### Availability

0.9.8.6_beta_2 +, 0.9.9.10+ for HTTP headers, authentication

### Behavior

Sends the specified content through the specified communication channel.

The first form sends the content using the defaults that are inferred from the
configuration of the channel or derived directly from the content itself. The
second form allows specification of lower level details of how the content
should be sent and the message.

The purpose of `send` is to enable explicitly sending content such as small
reports and status messages as part of the flow of your pipeline. The `send`
command can occur inside pipeline stages that perform other processing, or
stand alone as part of a dedicated pipeline stage.

The content can be specified in three different ways. The first option simply
specifies a literal text string that is used as the message directly. Note that
the text string *must* appear within curly braces. It will be lazily evaluated
just prior to sending. The `html` option allows creation of HTML content
programmatically. The body of the closure (that is, inside the curly braces) is
passed a [Groovy MarkupBuilder](http://groovy.codehaus.org/Creating+XML+using+Groovy's+MarkupBuilder).
This can be used to create HTML that forms the body of an HTML email. 

The next form allows specification of a template. The template should end with
an extension appropriate to the content type (for example, to send an HTML
email, make sure the template ends with ".html").  The template file is
processed as a [Groovy Template](http://groovy.codehaus.org/Groovy+Templates)
which allows references to variables using the normal `$` syntax, `${variable}`
form as well as complete Groovy programmatic logic within `<% %>`  and `<%= %>`
blocks.

A `json` utility method is available to format data as JSON. The `json` method
accepts a JSON-encodable object (eg: groovy/java list, map, string, integer, etc),
which is encoded to JSON and then sent as the body of the message.

*Note*: a common scenario is to terminate a branch of a pipeline due to a
failure or exceptional situation and to send a status message about that. To
make this convenient, the [succeed](Language/Succeed) and [fail](Language/Fail)
commands can accept the same syntax as `send`, but also have the effect of
terminating the execution of the current pipeline branch with a
corresponding status message.

The destination of a `send` is specified after the `to` portion. If the 
destination is specified as a string, it is expected to correspond to one of
the channels defined in the `notifications` section of the `bpipe.config` for 
the pipeline. This allows pipelines to be developed with generic notification
capability and for end users to configure how they would like to receive those
notifications. With the extended syntax, the pipeline code can specify options,
up to and including the full channel specification. Some more advanced
options are only available through this mechanism.

**Note**: In Bpipe's generic notification framework, configuring a
notification channel automatically enables certain events from the pipeline
operation to be forwarded to the channel. If you only want to receive
the events you explicitly send using `send`, you must set the 
`events` property for the channel to blank (`''`). See the 
full example below for more details.

### Examples

**Send a message via Google Talk**
```groovy 

    send text {"Hello there"} to gtalk
```

**Send a message via Gmail, including a subject line**
```groovy 

    send text {"Hello there, this is the message body"} to channel: gmail, subject: "This is an email from Bpipe"
```

**Send an HTML email to Gmail**
```groovy 

    send html {
        body { 
           h1("This Email is from Bpipe")
           table { 
               tr { th("Inputs") }
               inputs.each { i -> tr { td(i) }  }
           }
    } to gmail
```

**Send a message based on a template using Gmail, and attach the first output as a file**
```groovy 
    send report("report-template.html") to channel: gmail, file: output1.txt
```

**Send a JSON message to a REST style HTTP Serivce**

If the destination contains a `url` property and no other channel
is specified, Bpipe will generate an HTTP POST request to the URL:

```groovy
    def data = [hello:1, world:'mars']
    send json(data) to(url: 'http://my.server.com:12345/service/path')
```

The content type will be inferred from the content provided to the
send. For example, sending JSON will cause the content type to be
set to `application/json`. This can be over-ridden with custom 
headers.

Note: when specifying data inline, the above can be written more 
compactly using Groovy's syntax for passing Maps to functions like so:

```groovy
    send json(hello:1, world:'mars') to(url: 'http://my.server.com:12345/service/path')
```

**Send a JSON message to a REST style HTTP Serivce with Basic Authentication**

To provide basic authentication, add a username and password to the attributes
of the destination:

```groovy
    send json(hello:1, world:'mars') to url: 'http://my.server.com:12345/service/path',
                                        username: 'joe',
                                        password: 'supersecret'
```

**Adding Headers to an HTTP Request**

Headers can be added to HTTP requests as a map of key value pairs 

```groovy
    send json(hello:1, world:'mars') to url: 'http://my.server.com:12345/service/path',
                                        headers:[AUTH_TOKEN:'a custom secret for my special auth scheme']
```

**Send a JSON message to an ActiveMQ Queue**

To configure the connection, configure an activemq section in the `notifications`, eg:

```
notifications {
    analysis_finished_queue {
        type='activemq'
        queue='analysis_complete' // the actual activemq queue name
        brokerURL='tcp://127.0.0.1:61616' 
        events=''
    }
}
```

Notice we disable other events from being forwarded so that we only receive messages
explicitly sent through the connection.

```groovy
    send json(
        sample: sample, 
        sex: "Male"
        batch: batch,
        run_id: analysis_id
    ) to analysis_finished_queue
```

In the above, we take advantage of the Groovy abbreviated syntax for
specifying a Map as an argument to a function. The equivalent is:

```groovy
    send json(
        [
            sample: sample, 
            sex: "Male"
            batch: batch,
            run_id: analysis_id
        ]
    ) to analysis_finished_queue
```

**Send an Issue to Gitlab**

Note that you need to set up the Project, Gitlab URL and authentication token
in the `bpipe.config` file (see Gitlab Guide).

```
    send issue(                                                                         
            title: 'Hello there from bpipe',                            
            description: 'This issue was created by bpipe.\n\n- super awesome',         
            assignee: 'joe.bloggs',                                                  
            label: 'testlabel'                                                         
            ) to gitlab                                                                 
```

**Send to HTTP Configured in bpipe.config**

Create a `bpipe.config` file like this:

```groovy
notifications {
    my_http {
        type = 'http'
        url = 'http://localhost:8005/myservice'
        username = 'tester'
        password = 'password'
        events=''
    }
}
```

You can then send to it like this:


```groovy
    send json(hello:0, world:2) to 'my_http'
```

You can also send like this:

```groovy
    send json(hello:0, world:2) to channel: 'my_http',
                                   url: '/subpath'
```

The latter version will combine the URL paths from the bpipe.config
and pipeline command together, which is useful to allow you to specify
a base URL in the bpipe.config and separate paths in the pipeline for
different services.
