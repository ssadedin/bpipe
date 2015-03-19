# Notifications in Bpipe

## Introduction

Constantly checking a long running pipeline can be frustrating, especially if it fails and you are unaware that it needs attention.  To help with this, Bpipe can send notifications about pipeline events via email or XMPP (instant messaging).

## Configuration

To use notifications you need to create a Bpipe configuration file called `bpipe.config` in the local directory where the pipeline is running.  In this file, you can specify various ways to receive notifications, and set filters on which notifications you are to receive.    All notifications are configured in a "notifications" block, and each entry therein configures a separate notification.  You can configure as many as you want, as long as each one has a different name.

*Note*: you can place 'global' configuration for notifications in a file in your home directory called ".bpipeconfig".  These will be shared by all Bpipe pipelines in any directory, but any local configuration will override global configurations.

### Using Google Services

Bpipe provides simplified support for sending notifications to Google Accounts.  An example for receiving notifications via Google Talk is as follows:
```groovy 

notifications {
  gtalk {
    username="yoursenderaddress@gmail.com"
    password="your sender password"
    to="recipient@gmail.com"
  }
}
```

Note that for Google Talk, you have to have a separate *sender* account for which you enter credentials, and then a recipient account which is the account that gets notified.

To use Gmail, you can use a configuration like so:
```groovy 

notifications {
  gmail {
    to="recipient@any.email.com"
    username="your.address@gmail.com"
    password="your password"
  }
}
```

**Important Note 1** - to use Gmail notifications or SMTP notifications, you must download the [JavaMail](http://www.oracle.com/technetwork/java/javamail-138606.html) package from the Oracle web site and place the `mail.jar` library in the "local-lib" folder inside the Bpipe installation.


**Important Note 2** - GMail limits how many emails can be sent per day through its SMTP interface to prevent abuse.  If your pipeline tries to send more than 500 emails or so then you may find your account becomes blocked for a period of time.  Be careful about configuring notifications at a fine grained level (for commands or stages) in highly parallelized pipelines as you may easily cause Bpipe to try to send a hundred more emails in a short period of time which may be flagged as abuse.

**Security Note** - remember that you are putting real passwords in plain text into these files.   For this reason it is strongly suggested not to use your own, real, accounts for sending these messages unless you are extremely confident in the security of your files. Rather, it is better to create some custom accounts dedicated to your pipelines and have your messages sent by these.

### Using Generic SMTP

To use a generic SMTP server, create your notification block like so:
```groovy 

notifications {
  smtp {
    to="recipient@address.com"
    host="smtp host"
    secure=false
    port=25                   // optional
    username="your username"  
    password="your password"  // optional
    from="from@address.com"   // optional, defaults to username
  }
}
```

### Using Generic XMPP

```groovy 

notifications {
  xmpp {
    type="xmpp"
    to="recipient@address.com"
    host="xmpp server host"
    service="service name"
    port="port"
    username="username"
    password="password"
  }
}
```

### Multiple Notifications of the Same Kind

If you want to configure multiple different notifications of the same type then you need to name each one differently.  Bpipe automatically interprets the names "xmpp" and "smtp" as being XMPP and SMTP type notifications (and also "gtalk" and "gmail" respectively).  However if you create different names you need to also specify a `type` attribute that specifies what kind of notification is being sent.  For example, to send using two different GMail acccounts:

```groovy 

notifications {
  gmail1 {
    to="recipient@any.email.com"
    username="your.address@gmail.com"
    password="your password"
    type="gmail"
  }
  gmail2 {
    to="recipient@any.email.com"
    username="another.address@gmail.com"
    password="another password"
    type="gmail"    
  }
}
```

## Filtering Events for Notifications

You can choose which events you would like to receive notifications about. At the moment the following events are supported:

- FINISHED
- STAGE_COMPLETED
- STAGE_FAILED

If you don't specify anything then Bpipe defaults to FINISHED, which occurs only at the completion of your pipeline (whether success or failure).

To configure the events to receive notifications for, add an `events` line to your configuration. For example, to receive a Google Talk notification as each pipeline stage completes, a configuration like this could be used:
```groovy 

  gtalk {
    username="yoursenderaddress@gmail.com"
    password="your sender password"
    to="recipient@gmail.com"
    events="STAGE_COMPLETED" // receive notifications as each stage completes
  }
```

You can put multiple events in, separating them by commas.

## Customizing the Notifications

As of 0.9.8.6 beta 2, you can customize the text in the notifications.

The notifications are based on templates that are found in the "templates" folder inside the Bpipe installation directory. You can customize these by editing them directly, but you can also customize them by setting the "template" parameter in your bpipe.config file:

```groovy 

  gtalk {
    username="yoursenderaddress@gmail.com"
    password="your sender password"
    to="recipient@gmail.com"
    template="my.gtalk.template.txt"
  }
```

You can override the templates on a per-event basis as well:

```groovy 

  gtalk {
    username="yoursenderaddress@gmail.com"
    password="your sender password"
    to="recipient@gmail.com"
    templates {
        FINISHED="my.finished.template.txt"
    }
  }
```

When searching for a template, Bpipe will resolve files found in the local Bpipe directory ahead of those found in the Bpipe installation directory. Thus you can override the template for a particular instance of a pipeline via a local bpipe.config file.

## HTML and other Emails

By default Bpipe sticks to sending plain text notifications. However if you name your template ending with ".html", Bpipe will change the content type to "text/html" so that an HTML email is sent instead. At this stage you cannot embed images, although you can reference remote images.

## Sending Arbitrary Notifications

You may wish to send out notifications explicitly as part of your pipeline, rather than relying on Bpipe to do it in response to pipeline events. This is possible via the [send](Language/Send), [succeed](Language/Succeed) and [fail](Language/Fail) commands. See documentation on these commands for more information.







