# The run command

## Synopsis

    
    
        bpipe execute [-h] [-t] [-d <output folder>] [-v] [-r] <pipeline> [<input 1>, <input 2>,...]
    

## Options

The `execute` command accepts the same options as the [run] command.

*Note*: If you want to run in test mode (to see what commands will be executed before running them), supply the -t option.

## Description

Creates a Bpipe job for a pipeline defined on the command line and runs it.
This command causes the same behavior invoked by the `run` command, except that
the pipeline is not defined in a file but rather on the command line itself.
Since there is no way to define pipeline stages, all the stages used must be
defined by automatically loaded pipeline stages that are present in files in
the Bpipe lib directory (by default, `~/bpipes`, or you can set the
`$BPIPE_LIB` environment variable to change it. Alternatively, supply the `-s` option
to cause Bpipe to load pipeline stages from a file explicitly.

## Example 1 - Run an Ad Hoc Pipeline

Create a file called `stages.groovy` with the following contents:
```groovy 

hello = {
  exec 'echo hello'
}

world = {
  exec 'echo world'
}
```

Then execute:

```groovy 
  bpipe execute -s stages.groovy 'hello + world'
```

This behaves the same as creating a file, `test.groovy`:

```groovy 

  run { hello + world }
```

And running it using:
```groovy 

  bpipe run test.groovy
```

## Example 2 - Run a Single Stage

Create a file called `stages.groovy` with the following contents:
```groovy 

hello = {
  exec "cp $input $output"
}

world = {
  exec 'echo world'
}
```

Then execute a single stage from this file:

```bash 
  echo hello > test.txt
  bpipe execute -s stages.groovy hello test.txt
```


