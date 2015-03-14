# The retry command

## Synopsis

    
    
        bpipe retry [test]
    

## Description

Re-execute the most recently run Bpipe job.  This is typically how you can easily restart a job that may have failed or been interrupted from where it left off.

By adding *test* after the retry Bpipe will not actually execute the pipeline but instead show you the command that would be run if retry was used.

What retry actually does is rerun your whole pipeline from the start. However as it executes each pipeline stage Bpipe will check if the files expected to be created by the stage already exist and if so, and they are newer than all the inputs to the stage, it will skip executing the commands in the stage so that the pipeline moves rapidly until it reaches the first point that did not execute before.   Thus the key information that Bpipe uses to decide if a command within a pipeline stage should be re-executed is the file timestamps of the outputs of the command and the timestamps of the inputs to the command.

It should also be noted that when a command in a pipeline stage fails, Bpipe will 'clean up' the outputs of the command at the time of the failure.  Thus a pipeline stage that fails will not produce outputs and will be re-run when a retry is performed.

*Note*:  There is preliminary support for Bpipe also to recognize if the commands in the pipeline stage have been modified and to use that information when deciding whether they need to be re-executed.   This is done by saving the outputs created by each command executed in the local .bpipe folder so that when Bpipe sees outputs that are newer than inputs it will also then check if the command that created the outputs is the same as the new command to be executed.  This support is currently not enabled in the released version of Bpipe.

The main feature of 'retry' is that it automatically looks up the exact command that you ran the previous time you ran Bpipe in the local directory and re-executes it with the same options and input files. 