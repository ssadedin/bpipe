# The query command

## Synopsis

    
    
        bpipe query [<file1>] [<file2>]...
    

## Availability

0.9.8+

## Description

Displays information about outputs of a pipeline. With no arguments provided, query shows the dependency tree for the outputs in the current directory. This can be very large for a large number of outputs.

If specific files are provided as arguments, only the dependency tree for the specific files is shown, and in addition, detailed information about those files is displayed such as which command created the outputs, the direct inputs to the command, the date created and other meta data.

## Example 1

**Display the whole dependency graph for all outputs in the current directory**
```groovy 

bpipe query
```

## Example 2

**Query information about file foo.txt**
```groovy 

bpipe query foo.txt
```
