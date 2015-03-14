# The test command

## Synopsis

    
    
        bpipe test [-h] [-t] [-d] [-v] <pipeline file> [<input 1>, <input 2>,...]
    

## Description

Simulates execution of the pipeline until the first command is attempts to run.  Then aborts the pipeline, displaying the command to the console for inspection. 

Use the `test` command to debug your pipelines and check that long running commands are being correctly forumulated before they are executed.

Note: the `test` command can also be invoked using retry to test the command that will be executed if retry is performed:
```groovy 

bpipe retry test
```
