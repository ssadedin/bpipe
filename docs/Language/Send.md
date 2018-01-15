# The send statement

### Synopsis

    
    
    send [text {<text>} | html { <html> } | report(<template>)] to <notification channel name><pre>
    send [{<text>} | html { <html> } | report(<template>)](text) to channel:<channel name>, 
                                                               subject:<subject>, 
                                                               file: <file to attach> 
 
### Availability

0.9.8.6_beta_2 +

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

*Note*: a common scenario is to terminate a branch of a pipeline due to a failure or exceptional situation and to send a status message about that. To make this convenient, the [succeed](Language/Succeed) and [fail](Language/Fail) commands can accept the same syntax as `send`, but also have the effect of terminating the execution of the current pipeline branch with a corresponding status message.

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





