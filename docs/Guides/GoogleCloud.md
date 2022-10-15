## Using Bpipe with Google Cloud

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

Since Bpipe will be running commands using the GCloud SDK, you need to login using the SDK before
you run your Bpipe pipeline. Your login credentials will flow automaticlaly through to Bpipe.

```
gcloud auth application-default  login
```

## Storage

A key aspect of cloud based services is that they do not typically mount shared file systems that
a tool like Bpipe can see. Rather, most cloud systems offer storage in the form of "buckets". 
Bpipe bridges this gap by using the built in Java abstraction that lets java programs see remote files
as if they were local paths (NIO). To make it work, you need set up a *filesystem configuration*. This is 
accomplished in your `bpipe.config` by setting the storage type to `gcloud`, and then configuring a file system
called `gcloud` with the details of the bucket you want to use:

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
is separate and independent to the configuration of how the commands run. Note however that 
currently Bpipe is only being tested and developed using executors with their native storage options
(ie: Google Cloud executors with Google Cloud storage).


## Create an Image to Use

Bpipe will rely on gcloud commands, and in particular, gcs-fuse, being available on the instances that you
run. This is the case on many of the default images already available on Google Cloud. You should prepare
an image that already has the tools you want to run installed, and make sure that gcs-fuse works, as well
as the other Google Cloud commands. Note that Bpipe will access the image using a service account, not a 
manual login. 

## Create a Service Account

In general when using Google Cloud, operations that interact with other Google Cloud services are
authenticated via a service account (if you don't use a service account, the system will prompt for
authentication interactively which obviously not ideal for an automated pipeline!). Therefore you should
create a service account in Google Cloud and give it permissison to access your Google Cloud storage, as 
well as any other permissions needed for your pipeline to work.

## Configure the Executor

Once you have your image, your service account and region selected, configure them in the `bpipe.config` 
file:

```
executor='GoogleCloud'
image='your image id'
serviceAccount="<service account>" // jobs will run under this service account
region='australia-southeast1-b' // should actually point to a zone
```

If you want, you can also specify the machine type to use for your image:

```
machineType='n1-standard-1'
```

The valid machine types can be found in the Google Cloud documentation:

https://cloud.google.com/compute/docs/machine-types#predefined_machine_types

## Running Jobs

Once you have set up your configuration in your `bpipe.config` file, you can start 
your pipeline as normal:

```
bpipe run pipeline.groovy file1.txt file2.txt ...
```

Note that in this example, file1.txt and file2.txt do not exist locally, they are expected to
exist in the Google Cloud bucket you have configured for the storage. This is also where
the outputs will appear.



