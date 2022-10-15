[comment]: <> ( vim: ts=20 expandtab tw=100 spell nocindent nosmartindent filetype=Markdown)

## Debugging Bpipe Scripts

This section describes strategies and techniques for working out why your
pipelines are not behaving as you expect.

## Running in Debug Mode

Bpipe includes a method to allow you to invoke an interactive Groovy shell to probe
the state of a pipeline stage at a particular step within your pipeline. This can 
be very useful to work out why things are not working how you expect.

To initiate a debug session, do the following:

1. add a `debug()` call at the point you want debugging to be initiated

2. launch your pipeline using the `debug` command instead of the `run` command

When you do this, you will find Bpipe launches in a special "foreground" mode that means
the main bpipe process is attached to your console for interactive input. Note that this is 
a limited mode and some features of Bpipe are disabled - for example, if you use control-c to 
break out of the pipeline you will find it has died, possibly without killing any jobs 
that were running. You might have to clean up and run `bpipe stop` yourself.

When the pipeline reaches the point where you added the `debug()` call, you will find the
Bpipe process stops and drops you into a shell-like environment where you can type Groovy 
expressions and see the output.

## Using the Debug Shell

The debug shell is actually the standard Groovy REPL, but configured with the environment of your
pipeline and the context of your pipeline stage available.

However, some of the interception and "magic" variables that Bpipe normally provides are not
available, and have to be explicitly invoked. The normal functions and variables that you access
such as "input", etc are actually properties of a `PipelineContext` object that is where Bpipe
keeps its internal state related to each active pipeline stage. You can access the normal 
variables and methods as part of this object, which is provided as the `ctx` variable. For example, 
with this pipeline:

```
hello = {
    branch.name = 'foo'
}

world = {

    var bar : 'cat'

    debug()
}
```

you can run:

```
bpipe debug test.groovy  test.txt 
Launching in foreground due to debug flag set
[----]  ====================================================================================================
[----]  |                              Starting Pipeline at 2020-04-14 13:45                               |
[----]  ====================================================================================================
[]  
[]  =========================================== Stage hello ============================================
Groovy Shell (2.5.6, JVM: 1.8.0_131)
Type ':help' or ':h' for help.
-----------------------------------------------
groovy:000> 
```

You can then examine the `input`:

```
groovy:000> ctx.input
===> test.txt
```

You can view branch properties and variables using `ctx.branch`, and locally defined variables as `ctx.localVariables`:

```
groovy:000> ctx.localVariables
===> [bar:cat]
```

