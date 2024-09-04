## Gitlab Integration

Bpipe supports integration with Gitlab, which enables you to create 
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

**NOTE**: the issue will be identified by substring search of the title attribte. Any issue
containing the title in either the description or the title will match. To apply stricter 
matching, use the Advanced Search Syntax.

## Advanced Search Syntax

More flexiblity can be obtained in identifying the issue to match by supplying a Groovy Map as the 
title. The map should have two attributes:

- search : text to search for in either the issue title or body
- match  : a regular expression that must be found within the *title* of the issue found

If no issue is identified using the advanced syntax then no issue will be created at all.

## Attaching Files and Embedding Images

Gitlab support also includes ability to attach files from your pipeline outputs. To enable 
a file to be attached, it first has to be uploaded. To trigger the upload, add it as part of the
configuration for the `send` using the extended send syntax:

```
    send issue(                                                                         
    ... details ...
    ) to channel: 'gitlab', file: input.txt
```

This will cause the file to be uploaded and an upload URL will be created. Uploaded files then become available for
reference in the description of the issue, simply by embeddedin them using the usual markdown syntax for files:

```
    send issue(                                                                         
        description: "This file contains the results: [results file]($input.txt)"
    ) to channel: 'gitlab', file: input.txt
```

Note that the `input.txt` referenced in the description has to be the same one that you
reference in the channel configuration.

_Note_: at this time only one file can be attached.

Embedding images is the same as attaching files. You simply prefix the file reference
in your description with a `!` to cause it to be rendered in the issue instead of treated
as an attachment. For example:

```
    send issue(                                                                         
        title: 'Results of Experiment',
        description: "This figure shows my results:\n ![Figure 1]($input.png)"
    ) to channel: 'gitlab', file: input.png
```

## Description Templates

You can use a template file for the description if you wish. To do this:

- ensure any variables needed in the template are included in the `issue(...)` section as attributes
- add a `template : "<path to your template file>"` attribute also.
- do not include the description attribute

For example:

```
    send issue(                                                                         
        title: 'Results of Experiment',
        template: 'templates/experiment_results.md'
    ) to channel: 'gitlab', file: input.png
```

## Gitlab Actions

You can cause various actions to occur such as adding and removing labels, assigning issues
to people or setting a due date by embedding [Quick Actions](https://docs.gitlab.com/ee/user/project/quick_actions.html) 
in the body of the description.

For example, to mark an issue as due in 2 days:

```
The analysis for sample $sample has finished.

Please follow up to do the QC checks.

/due in 2 days
```



