[comment]: <> ( vim: ts=20 expandtab tw=100 spell nocindent nosmartindent filetype=Markdown)

# Gitlab Integration

Bpipe supports simple integration with Gitlab, which enables you to create 
issues and add notes to them as part of your Bpipe pipeline.

## Configuration

Gitlab integration is accomplished through the existing framework for sending
notifications. To configure it, you need to set up a notification channel
of type 'gitlab' in your `bpipe.config` file for your pipeline. There
are three important settings:

 - the URL of your Gitlab server
 - the project within which you want to integrate
 - an authentication token, generated from your settings page in Gitlab

Note that all integration is done within the context of a particular project.
If you want to communicate with more than one project, they should be configured
as separate notification channels.

Here is an example configuration:

```
notifications {
    gitlab {
        url='http://git.server.com'
        project='test_project'
        token="your-secret-token"
        events=''
    }
}
```

## Usage in Pipeline Scripts

Interaction with Gitlab within pipeline scripts is accomplished using
the `send` command, in the form:

```
send issue(... details...) to gitlab
```

The `gitlab` in the above expression is the name of the Gitlab notification
channel, which is `gitlab` by default, but can be a custom name if you 
set it up that way in the notification configuration.

To create an issue you must at least specify the title. The other parameters
are optional and include label, assignee and description. A full example is
as follows:

```
    send issue(                                                                         
            title: 'Hello there from Bpipe',                            
            description: 'This issue was created by bpipe.\n\n- super awesome',         
            assignee: 'joe.bloggs',                                                  
            label: 'testlabel' 
            ) to gitlab
```

If you run this once, it will create the issue. If you run it again before closing
the issue, it will add a note to the issue instead.
