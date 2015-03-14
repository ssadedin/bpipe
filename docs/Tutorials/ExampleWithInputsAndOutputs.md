# How Bpipe handles inputs and outputs

The whole point of a pipeline is to take some existing data you have, do some work on it and produce some outputs.  This example will expand the GettingStarted tutorial to add inputs and outputs.

## Details

To demonstrate how inputs and outputs work, we need a file to be an input to your pipeline.  For this example, create a file called "test.txt" with just the word "Bpipe" in it - or any word you like.  For example, execute:
```groovy 

  echo Bpipe > test.txt
```

Now we have an *input* to our script, we can modify our script to use that input.  Edit your file from the GettingStarted example and change it to look like the code below:
```groovy 

hello = {
  exec "echo Hello | cat - $input > $output"
}
world = {
  exec "echo World | cat $input - > $output"
}
run { hello + world }
```

To run this example you need to supply the input file as an argument, like so:
```groovy 

bpipe run helloworld.pipe test.txt
```

## Explanation

In this example you can see that our commands still look like normal shell commands that you might have executed at the command line, however there are two conspicuous additions that look like shell variables:  $input and $output.  These variables are defined for you *implicitly* by Bpipe before each pipeline stage is run.  They automatically tell you where the input is coming from and where the output should be going.  You *could*, of course, hard code the file names into your commands, but then your pipeline would depend on those file names and you would not be able to "plug and play" with your pipeline stages to connect them in different ways.  Since you aren't specifying the output file you might wonder where the actual output went.  Bpipe uses a systematic naming convention for files so that they are reliably the same for any given pipeline configuration but reliably different if you change the pipeline.  To achieve this, Bpipe builds file names by naming them according to all the pipeline stages through which the file has "passed", starting with the input file as a base.  In this case, Bpipe will have called your output as follows:
```groovy 

test.txt.hello.world
```

Bpipe created this file name by starting with the input file and then adding the segments "hello" and then "world" for each stage that was processed.  You might find it a little strange to see ".txt" in the middle of the file name:  fear not, this way of naming is merely the default behavior for Bpipe when it does not know the kind of file you are creating.   The next steps in the tutorial show you how to change this.

## Implicitly Specifying File Types

Bpipe offers a quick and simple way to specify file types for input and output files: adding the extension of the type of file to the $input and $output variables.  For example, you can tell Bpipe that the files in the above pipeline are text files like so:
```groovy 

hello = {
  exec "echo Hello | cat - $input.txt > $output.txt"
}
world = {
  exec "echo World | cat $input.txt - > $output.txt"
}
run { hello + world }
```

This pipeline works similarly to the previous one, but has two advantages: Bpipe is now checking that the input file is a text file; if you try to run it on a file with some other extension then you'll get an error.  Also, Bpipe now knows that the output files should end in ".txt" because you indicated that by writing `$output.txt`.  So now our output files look like this:
```groovy 

test.txt
test.hello.txt
test.hello.world.txt
```

By understanding Bpipe file naming conventions you can now easily see which operations have happened to any file by looking at its file name:

- test.txt was the input file
- test.hello.txt was created from test.txt by passing through the "hello" pipeline stage
- test.hello.world.txt was created from test.hello.txt by passing through the "world"  pipeline stage

This convention is part of how Bpipe helps to ensure you are never confused about how any file was created.

*Note*: As an extra benefit, this small change is also enough that Bpipe will now perform dependency checking; that is, if the output files are newer than the input files, Bpipe won't execute the steps to create them.

## Annotating Pipeline Stage Types

While the above pipeline works we can make it just a little better by giving Bpipe a few small hints about what each stage in the pipeline is doing.  To do this, we can add some *annotations* to the pipeline stages as follows:
```groovy 

@Filter("hello")
hello = {
  exec "echo Hello | cat - $input > $output"
}
@Filter("world")
world = {
  exec "echo World | cat $input - > $output"
}
```

Here we have added `@Filter` annotations to each stage, identifying a name for the kind of filtering ("hello" and "world").  We call it filtering because it involves modifying a file without changing its type.  Since the input file is a text file and the output is also a text file, this makes our example a Filter operation.   On the other hand, operations that change the format or convert it to a different format are called Transform operations in Bpipe.  Hence if we were changing the file to another format (for example, CSV or XML) we would instead annotate it with @Transform.

The result of adding Filter annotations to our script is very similar to adding file extensions to our `$input` and `$output` variables.  However it can be a little clearer to express your intentions by declaring about what a stage does by using @Filter or @Transform, so these can be preferable.  However the choice is up to you.