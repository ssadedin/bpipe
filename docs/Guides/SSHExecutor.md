## Generic SSH Executor

## Introduction

While Bpipe supports many different resource managers, you may encounter situations where you simply
need to execute a command on a fixed remote server. It can also be the case that you wish to use
a resource manager that is not directly supported by Bpipe, and in these situations you orchestrate
allocation of the resources outside of Bpipe but need Bpipe to simply run a command on the target host.

In these scenarios, the `SSH Executor` may be useful. It allows you to simply specify a host name and
SSH credentials, and Bpipe will then use SSH to execute the command in your pipeline on the remote
host. In addition to executing the remote command, the SSH Executor also supports functionality
to transfer files to and from the remote host, if it is not integrated with the source host with
a shared file system.

## Configuration

To run an SSH Executor, configure the `SSH` type in the executor setting, and then set the hostname
and username in a sub-section called `ssh_executor`:

```
executor="SSH"

ssh_executor {
  hostname="my.compute.server"
  user="myuser"
}
```

These settings will cause Bpipe to try to run commands on the remote server using SSH to
execute them.

## Keys

- can set keypair setting in config

## File Transfer

Set `transfer=true` at higher level to cause Bpipe to copy files to and from the target host
using SSH. Note that the user provided must have permissions to create the directory path
that the pipeline is running in on the target host, as well as copy files to and from it.
