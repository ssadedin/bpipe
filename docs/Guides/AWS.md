# Using Bpipe with Amazon Web Services Elastic Compute Cloud (EC2)

## Introduction

Bpipe supports running jobs on Amazon WebServices Elastic Compute Cloud (EC2) infrastructure.
This section describes how to setup Bpipe to launch jobs using your AWS account.

_Note_: Cloud support is still under development, and should be considered beta functionality.

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
        type='S3'
        bucket='<name of bucket to use as work directory>'
        accessKey = '<your access key>'
        accessSecret = '<your secret access key>'
    }
}
```

NOTE: in the current implementation, it is necessary to redundantly specify the access key and secret
for both the storage and the executor.

NOTE2: the name of the key pair is expected to match the file name it is stored in, absent the `.pem`
extension. For example, if you call the file `my_secret_key.pem` then Bpipe expects the key is
called in AWS `my_secret_key`.

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



