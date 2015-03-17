# How Bpipe Logs Information

An important benefit of Bpipe is that it automatically keeps logs of all your pipeline runs so that you can later go back and verify exactly what happened when you need to reproduce your results or understand how a result was generated.  This section documents the kinds of logs that Bpipe keeps and what information is stored in them.

## Command Logs

The command log is stored in a file called `commandlog.txt` in the local directory where the job is running.  It is a file that is continuously appended to as each and every job is run to create an ever growing log of every command that was executed. 

The command log starts each pipeline execution with a header that includes the date, time, and specific input files supplied.   Then this information is followed by stage names headers under which each fully realized command that is executed by Bpipe in that stage is listed.  By "fully realized" we mean that all variables are fully evaluated so that there is no ambiguity about what *actually* executed. 

A very simple example of how the command log looks is below:
```groovy 

####################################################################################################
# Starting pipeline at Wed Feb 01 15:22:01 EST 2012 as Job 4225
# Input files:  test.txt 

# Stage hello
cp test.txt  ./test.csv
# Stage world
cp ./test.csv ./test.xml
```

### Program Versions

Although Bpipe fully evaluates variables in the commands you run, you may still have ambiguity about which exact programs executed and their versions.  Bpipe understands how to extract version information from a predefined set of programs that are commonly used.  When Bpipe is able to extract this information it is logged in the HTML report. 

You can extend this yourself by putting the tools you use into the [Tool Version Database](Guides/ToolVersionDatabase).

## Output logs

To keep them clear and uncluttered the command logs do not contain the actual output from the commands that executed.   If you wish to view the output you can do so by reviewing the full job Output Log.   You can view the output log for the most recent or currently running job by typing 
```groovy 

bpipe log
```

which will stream the output from the job continuously to your console.  If you wish to see the output log for an older job you need to find its *Job Id*, which you can see in the command log.  Then you can find the log archived in directory called `'.bpipe/logs/[Job Id].log'`.

## Trace logs

Trace logs contain internal Bpipe logging that details everything that Bpipe did for a particular job and why exactly it did those things.   You won't ordinarily wish to view this log, however if you can't understand why Bpipe is doing something or the other logs contain insufficient information for you to understand what happened, viewing the trace log can help to understand things better.  If you wish to file a bug then the output from the trace log will be highly valuable.  You can find the trace log for any Bpipe run by looking at the file `'.bpipe/logs/[Job Id].bpipe.log'`.
