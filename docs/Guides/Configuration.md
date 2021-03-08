[comment]: <> ( vim: ts=20 expandtab tw=100 spell nocindent nosmartindent filetype=Markdown)

## Pipeline Configuration

This section deals with how to create end-user editable configuration files for parameters, variables
and environment for your pipelines. Configuration is a key topic for computational
pipelines as it is very common that a pipeline needs to be generalised - whether to run
on different types of input data, using different computational resources, with different
sets of reference data or other static resources, or many other types of settings that
can differ between different contexts in which the pipeline may run. Therefore you
need a way to specify sets of useful default values while allowing the end user to 
easily customise the values themselves in flexible ways.

Bpipe offers features to achieve this.

## Precedence of Configuration Files

Although Bpipe offers several different ways to reference configuration settings, there is
a common patterns to which are used in a setting where more than one value is available to 
choose from. This is:

- if a setting is provided on the command line when Bpipe is invoked, this is used
- otherwise, Bpipe will look for the setting in any configuration loaded from the local 
  directory where the pipeline is running
- otherwise, Bpipe will try to load the setting from configuration set inside the directory
  where the pipeline file is located
- finally, Bpipe will load values from defaults that may be stored in `.bpipeconfig` in the user's
  home directory

With this in mind, if you are the pipeline author, the most common pattern is to put configuration
settings into files that are stored in the same directory as your pipeline files. This way, the user
can override them by adding configuration files into the directory where they are running the pipeline,
or specify overrides on the command line.

In general, for user specific global preferences (for example, the user's email address), you would
leave this as a setting for them to provide in their `~/.bpipeconfig` file.

## The Bpipe Config File

The main configuration file you should use for settings for your script should be the `bpipe.config` file.
This file can be placed in the same directory as your pipeline file, and you can set in there
configuration for both how commands are executed (directly on a server, or on an HPC cluster, for example), and
also for the environment and settings those command use when they execute.

### Tools

You can set the default location of some common tools that are used in computational workflows by setting 
their locations in the `bpipe.config` file:

- groovy:

```
groovy {
    executable='/some/path/to/groovy/binary'
}
```

- python
```
python {
    executable='/some/path/to/python/binary'
}
```


- R
```
R {
    executable='/some/path/to/Rscript/binary'
}
```

These configurations will be used when their inline scripting functions are invoked within 
Bpipe pipelines. In some cases relevant environment variables will also be inferred and set
for your commands as well.

### Command Configuration

The resources allocate to any given job often need to be customised to suit either the 
particular compute environment a pipeline is running in, or to the data that is being 
analysed.

To customise configuration for these settings, you can create a `commands` section in your
`bpipe.config` file. The options for configuring commands are described in detail in [Resource Managers](ResourceManagers.md).

### Environment Variables

Environment variables can be set for commands using an `env` block. This can be set globally or within 
the specific commands section:

```groovy
command {
    vep {
        env {
            PERL5LIB="/home/perl/lib"
        }
    }
}
```


### Per-Environment Settings

Groovy offers a standard way to allow for multiple environments within a single configuration file. For example,
you can have different settings for development, test and production within one `bpipe.config` file. To use 
a specific environment, pass the `--env` flag to Bpipe and provide the environment name. Then, you may create
an `environments` block within your `bpipe.config` and put environment specific configuration within there, with
one block for each environment containing overrides for that specific environment.


```groovy

SERVER='http://dev.server/'

environments {
   prod {
      parameters {
          SERVER=http://prod.server/
          commands {
              bwa {
                memory="32g"
              }
          }
      }
   }
   test {
      parameters {
          SERVER=http://test.server/
      }
   }
}
```

## Loading Configuration Directly

While most configuration can be accomplished with a `bpipe.config` file you may prefer to separate 
configuration variables from the runtime execution configuration. If you would like to do this,
a simple way is to use Bpipe's `load` statement to load a file, which is commonly called `config.groovy`
in the same directory as your pipeline files.

For example you can have `config.groovy`:

```groovy
REF='/some/reference/file.fasta'
```

And then a pipeline that makes use of the configured values:

```groovy
load 'config.groovy'

do_something = {
    requires REF : "The reference file to use"
    exec "sometool -R $REF"
}

run {
    do_something
}
```

The user can still override the configuration in `config.groovy` by supplying a `config.groovy` in their own
runtime folder, or they can override values individually when launching Bpipe:

```
bpipe run -p REF=/a/different/file.fasta pipeline.groovy ...
```
