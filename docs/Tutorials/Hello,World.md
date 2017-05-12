# First steps to using Bpipe


Bpipe is designed to be easy to get started with.  Make sure you've installed Bpipe first using the InstallInstructions, then follow the steps below to make your first pipeline.

## Hello World

Enter the following lines into a text file and save it as helloworld.pipe:

```groovy 
hello = {
  exec "echo Hello"
}
world = {
  exec "echo World"
}

Bpipe.run { hello + world }

```

Now type:

    bpipe run helloworld.pipe

## Explanation

The script above has several pieces.  The first 3 lines declare a pipeline stage called "hello":

```groovy 

hello = {
  exec "echo Hello"
}
```

This pipeline stage executes the command in double quotes - a simple "echo" as follows:
```groovy 

  echo Hello
```

It does this using a Bpipe command called "exec".  The exec statement passes the command through to a bash shell to be executed under the covers.   Before executing the command it logs the execution to a file so that you can see that it executed, what time it started and finished.  It also captures both standard and error output from the command and records it in a pipeline log file.  If the command fails, it aborts the script.   All of these "extras" are things you get by executing the command through Bpipe instead of directly through the bash shell.

The "world" pipeline stage is exactly the same in its form as the "hello" stage, so the next interesting part is 
```groovy 

Bpipe.run { hello + world }
```

Inside the curly braces the "hello + world" is *joining* the two stages together to make a pipeline using a "+" operator.  This pipeline is then being passed to Bpipe to run.   Without this statement your Bpipe script would just be defining pipeline stages without running them.  At first you might wonder why one would do that, but as your pipelines evolve you will realize that this is actually a very important feature of Bpipe - over time you will build up a library of pipeline stages that can be mixed and matched and reused, creating different pipelines from different sets of stages to suit the needs of a particular project without changing the definition of the commands that run each stage at all.

*Tip:  Since the task definition above uses double quotes you might be wondering what happens if the command you want to run uses double quotes itself.   For these cases (and many other cases) you can use triple quotes around your statement as shown below:*
```groovy 

hello = {
    exec """
        echo "hello" 
    """
}
```

Of course, this particular pipeline is very boring because it has no inputs and
other than displaying messages it produces no outputs.  To see how Bpipe
manages inputs and outputs, have a look at the next part of the tutorial:
[Example With Inputs And Outputs](ExampleWithInputsAndOutputs)
