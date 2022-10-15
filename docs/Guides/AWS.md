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

storage='S3'

filesystems {
    S3 {
        bucket='<name of bucket to use as work directory>'
    }
}
```

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
should contain all the software that you need to run the tools in your pipeline. In addition,
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



