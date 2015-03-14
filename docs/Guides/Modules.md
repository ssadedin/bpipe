# Modularizing Bpipe Scripts

An important aspect of Bpipe is making your pipelines maintainable and reuseable.  In order to achieve this it is greatly beneficial to put commonly used pipeline stages in a place where you can reuse them rather than copying them between pipeline files.   Bpipe makes it very easy to share a definition of a pipeline stage across multiple pipelines.

## Details

Bpipe looks in a folder in your home directory called "bpipes" for files that can define pipeline stages.   When you run bpipe, it loads all the pipeline stages from any file in this folder that ends with the ".groovy" file extension.    When you save such files, all the pipeline stages and variables defined in there become available to any pipelines that you run.

_Note: you can define an environment variable called BPIPE_LIB to control which folder Bpipe will look in for shared pipeline stages.  In the future you will be able to define BPIPE_PATH as a colon separated list of places for Bpipe to look instead_.

*Note2: the functions, objects and pipeline stages you define in external files are only available for reference within your pipeline stages and pipeline definitions. That is, you cannot reference them directly in the groovy script itself at the top level.*

## Example

Make a directory called "~/bpipes":
```groovy 

mkdir ~/bpipes
```

Now create a file in the folder called Hello.groovy containing a pipeline stage:
```groovy 

WORLD="world"
hello = {
    exec 'echo "hello $WORLD"'
}
```

Now in a separate folder, you can create a new file that uses this pipeline stage:
```groovy 

Bpipe.run {
    hello
}
```

Using this technique you can separate out common pipeline stages into appropriate files and reuse them across your Bpipe scripts.