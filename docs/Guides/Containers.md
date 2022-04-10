# Docker and Singularity Support

## Introduction

Using container systems such as Docker and Singularity is an increasingly common
way to encapsulate dependencies and improve portability of pipelines. While it is 
straightforward to simply alter you commands to run inside containers directly 
within your Bpipe scripts, this makes the pipeline specific to and dependent on using 
a specific container technology which actually makes it less portable. It can also
be awkward to have to repetitively supply all the necessary arguments to the container 
runtime to ensure required storage is made available to the container. Therefore
Bpipe provides support to specify the container runtime as part of command configuration,
allowing you to write your pipelines generically and have the user configure the container
setup.

## Enabling Container Use

To configure a command to run inside a container, add the `container` key to its 
configuration, with subkeys `type` and `image` specifying the container runtime to use (`singularity` or `docker`), 
and the name of the container image. It is expected that these are available within the execution environment.

**Example: Run Command in Ubuntu 14.04 Image**

```groovy
commands {
    my_command {
        container {
            type='docker'
            image='ubuntu:14.04'
        }
    }
}
```

Then in your Bpipe script:

```groovy
my_stage = {
    exec "echo 'hello world'","my_command"
}

run { my_stage }
```

**Example: Run VEP in Singularity Container**

```groovy
commands {
    vep {
        container {
            type='singularity'
            image='vep.sif'
        }
    }
}
```

Then in your Bpipe script:

```groovy
annotate_vcf = {
    exec "vep ... "
}

run { annotate_vcf }
```

Note that the command itself does not have any reference to container settings, which 
is what allows you to create portable pipeline scripts that can run inside or outside of
a container system.

You can cause all commands to run containerised by setting it as a root property:

```groovy
container {
    type='docker'
    image='ubuntu:14.04'
}
```

## Container Entry Points

An important aspect of containers relates to how the command is actually run inside the container.
The most common way to build containers is to have a container encapsulate a specific command, 
such that `docker run <image>` effectively substitutes for running the same command locally.

This approach, however, is difficult to incorporate into a pipeline without injecting container
specific logic into the commands themselves, because Bpipe cannot know which part of the command
you have specified should be replaced by the `docker run`.

To make it easier to generalise pipelines, by default, Bpipe *does not* use the default
entry point provided by containers, and instead, configures the runtime to execute
the explicit command that you provide.

A result of this is that if your container provides a complex or custom entrypoint that is necessary
for commands to work, you may need to trigger this yourself. For example,
if the target executable is not in the default path provided by the container environment,
you might need to resolve the absolute path of the executable and supply that in your pipeline
script.

For docker containers, you can add two configuration parameters to your container if you 
need to customise this:

- entryPoint : a String specifying the entry point to use. use "default" to cause
  bpipe to use the default entrypoint specified by the container
- command : a String or list of strings specifying the shell command used to launch
            commands within containers. By default, Bpipe launches commands by
            passing them to `/bin/sh -e`.

### Image References

The image property fo the configuration is passed directly to the container runtime. This means
you can use either remote image URLs that will be pulled and built automatically or you can pass
a locally resolved image path (for example, for Singularity, you can pass the absolute path 
to a SIF file).

As a convenience, with Singularity if the image path is a file path and is not resolved relative
to the current directory or as an absolute path, Bpipe will also check within the
`containers` directory within the direcotry that the pipeline script resides. This allows you 
to ship a `containers` directory with your pipeline that contains all the images needed for it to
run.

## Reusing Container Configurations

In some situations you may wish to use the same container settings across many commands.

To do this, create a `containers` section in your configuration. Inside, create subkeys that
name reusable container settings. You may then use these names as values for the `container`
key for your commands.

**Example: Use Ubuntu 14.04 for Two Different commands**

```groovy
commands {
    hello { container='ubuntu' }
    world { container='ubuntu' }
}

containers {
    ubuntu {
        type='singularity'
        image='ubuntu:14.04'
    }
}
```

## Binding Storage

Without any special configuration, the storage available to the container runtime will follow the 
defaults that apply to the container system. Additionally, Bpipe will bind the current working directory
into the container.

To bind other storage storage into your container, configure them first as file systems with the `bind` type:

```groovy
filesystems {
    reference_data {
        type='bind'
        base='/storage/reference_data'
    }
}
```

Then you may add these named file systems as storage for your containers:

```groovy
containers {
    ubuntu {
        type='singularity'
        image='ubuntu:14.04'
        storage='reference_data'
    }
}
```

When configured this way, Bpipe will add the required instructions to bind the paths into your
container.
