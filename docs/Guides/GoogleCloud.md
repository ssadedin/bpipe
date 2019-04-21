# Using Bpipe with Google Cloud

## Introduction

Cloud computing is becoming of increasing importance for running pipelines, but it imposes
a very different model on how tools like Bpipe need to work. Nonetheless, Bpipe now supports running
jobs on Google Cloud, as long as you live with a few constraints. This section tells you how
to set up a pipeline to run jobs on Google Cloud.

_Note_: Cloud support is still under development, and should be considered beta functionality.

## Install / Configure Google Cloud SDK

Bpipe performs most interactions with Google Cloud by using tools that are shipped in the
Google Cloud SDK. So the first step is to download these tools somewhere on your computer,
unzip them and set them up (follow Google's instructions for this), and then point 
to them in your `bpipe.config` file:

```
gcloud {
    sdk='/Users/you/google-cloud-sdk'
}

```

## Storage

A key aspect of cloud based services is that they do not typically mount shared file systems that
a tool like Bpipe can see. Rather, most cloud systems offer storage in the form of "buckets". 
Bpipe bridges this gap by using Java NIO to access remote files as if they were local paths. To 
do this, you need set up a *storage configuration*. This is accomplished in your `bpipe.config` by setting
the storage type to `gcloud`, and then the bucket:

```
storage="gcloud"

filesystems {
    gcloud {
        type='GoogleCloud'
        bucket='your bucket'
        region='australia-southeast1-b' // note this shoudl actually point to a zone
    }
}
```

**Note**: as an aside, the design of Bpipe is such that storage and compute are independent of each other.
So in *theory* you could have your Google Cloud commands run against AWS storage, or SSH storage, etc.
Obviously, that doesn't make any sense, but it may help you to understand why the storage configuration
is separate and independent to the configuration of how the commands run.


## Configure the Executor

The executor needs a couple more configuration settings to work:

```
executor='GoogleCloud'
image='your image id'
serviceAccount="<service account>" // jobs will run under this service account
region='australia-southeast1-b' // should actually point to a zone
```

## Running Jobs

Once you have set up your configuration in your `bpipe.config` file, you can start 
your pipeline as normal:

```
bpipe run pipeline.groovy file1.txt file2.txt ...
```

Note that in this example, file1.txt and file2.txt do not exist locally, they are expected to
exist in the Google Cloud bucket you have configured for the storage. This is also where
the outputs will appear.



