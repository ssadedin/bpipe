## Using Bpipe with Amazon Web Services Elastic Compute Cloud (EC2)

## Introduction

Bpipe supports running jobs on Amazon WebServices Elastic Compute Cloud (EC2) infrastructure.
This section describes how to setup Bpipe to launch jobs using your AWS account.

## Configuration

To run jobs on EC2, you should configure a number of settings in your `bpipe.config` file. Follow the 
example below:

```
executor="AWSEC2"
accessKey = '<your access key>'
accessSecret = '<your secret access key>'
keypair='<path to pem file>'
securityGroup='<security group to use>'
image= 'ami-02b30ce5afc4b9c26' // ami to use
user = 'centos' // unix username to ssh as
region = 'ap-southeast-2' // region to create instances in
instanceType = 't2.small' // type of instance to use

// If using S3 buckets for data transfer
storage='S3' 
filesystems {
    S3 {
        bucket='<name of bucket to use as work directory>'
    }
}
```

## Storage Management

Bpipe offers two options for provisioning access to input and output files to EC2
instances. These are:

- using S3 buckets. In this case, a bucket is mounted within your instance automatically
  and is used by commands as if it is part of native storage. This option is
  better if your whole pipeline consisting of multiple stages is hosted in AWS.
- SSH (scp) transfer. In this case, Bpipe uses `scp` to transfer files to the instance
  and back again. This option works well for hybrid pipelines when only specific steps use
  remote EC2 instances.

To use the transfer option, set `transfer=true` in your configuration. In that scenario, you do
not need to configure an S3 bucket.

To use the S3 bucket option, set up the S3 bucket as a file system as shown in the example
above. This file system must be configured as the file system for all the commands that
need to run in EC2 instances.

### Security Groups

Bpipe interacts with EC2 instances almost entirely using SSH connections. As such, it is important
that you ensure that instances created are assigned a security group that enables SSH
connectivity (that is, access on port 22). A convenient way to do this is to create a 
group with inbound rules:

- Type SSH
- Protocol TCP
- Port range 22
- Source 0.0.0.0/0  (or specify your port range)

You can then specify this security group in your `bpipe.config` file for all cloud access.

### Key Pairs

It is expected that you have already created and downloaded a key pair to allow
SSH access. Bpipe follows a convention that the name of the key pair is expected to match 
the file name it is stored in, absent the `.pem` extension. For example, if you call the 
file `my_secret_key.pem` then Bpipe expects the key is called in AWS `my_secret_key`. This
convention avoids you having to specify both the name of the key pair and its path.

## Preparing an image

As shown in the example above, one thing you must provide is an image id. This image
should contain all the software that you need to run the tools in your pipeline.

If you wish to utilise S3 storage to transfer and host analysis files,
you should also add the s3fs-fuse. This will allow Bpipe to mount buckets for you onto your 
instances, so that your pipelines can transparent read and write files from S3 buckets instead of 
having to copy them to and from the instance. Follow the instructions from the sf3fs-fuse 
web page to install it via SSH on your EC2 instance:

https://github.com/s3fs-fuse/s3fs-fuse

Once this is done, save an image of the instance using the AWS console, and set the image
id in your configuration.

## Running Jobs

Once you have set up your configuration, created a suitable image (AMI), and set its image id in 
your `bpipe.config` file, you can start your pipeline as normal:

```
bpipe run pipeline.groovy file1.txt file2.txt ...
```

Note that in this example, file1.txt and file2.txt do not exist locally, they are expected to
exist in the S3 bucket you have configured above.


## Using pre-existing instances

You may wish to use an instance that is already in existence to launch your jobs. You can achieve
this by specifying an instance id in your configuration (in `bpipe.config`):

```
instanceId='<the instance id>'
autoShutdown=false
```

This approach is especially useful during development because you do not have to wait for the
instance to start up, and Bpipe will use it straight away.  Note that in this scenario
you will usually want to turn off automatic shutdown by setting the `autoShutdown` flag. If you
do not specify this, Bpipe will shut down the instance when it is finished, even though it
did not start the instance itself.


## Robustness to Capacity Limits

If acquisition of an instance fails due to a temporary capacity limit such as 
`InsufficientInstanceCapacity` or `VcpuLimitExceeded` error codes, Bpipe will execute
a retry / wait loop where it will re-attempt at 5 minute intervals. By default,
Bpipe will retry up to 20 times to acquire an instance. You can modify this 
by setting the `instanceRetryAttempts` property in the cloud executor configuration,
and you can modify the interval of retries with the `instanceRetryInterval` property.


