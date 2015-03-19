# The run command

## Synopsis

    
    
        bpipe run [-h] [-t] [-d <output folder>] [-n <threads>] 
                  [-m <memory MB>] [-l <name>=<value>] [-v] [-r] 
                   <pipeline file> [<input 1>, <input 2>,...]
    

## Options

<table>
  <tr><td>-h</td><td>Show help and exit</td></tr>
  <tr><td>-v</td><td>Display verbose / debug logging</td></tr>
  <tr><td>-r</td><td>Generate HTML report of run in `doc` directory</td></tr>
  <tr><td>-d</td><td>Generate outputs to folder instead of current directory</td></tr>
  <tr><td>-t</td><td>Run in test mode (see [test](test) command)</td></tr>
  <tr><td>-n</td><td>Limit concurrency to at most `n` simultaneous parallel branches</td></tr>
  <tr><td>-m</td><td>Limit memory usage to specified amount in MB (0.9.8+)</td></tr>
  <tr><td>-l</td><td>Specify a custom limit (0.9.8+)</td></tr>
  <tr><td>-p</td><td>Specify a parameter (variable) value</td></tr>
</table>

## Description

Creates a Bpipe job for the pipeline defined in the specified file and runs it.  

The job runs in the background, detached from the current terminal (via nohup), but forwarding output to the terminal.

The -n option limits concurrency that Bpipe itself initiates, however Bpipe will not prevent tasks that it launches from using concurrency themselves. So if your commands themselves are spawning child processes or are multithreaded then you will need to account for that by supplying a smaller number to the -n option if you wish to have an absolute limit on processes or number of cores used.

The -m and -l options add limits that can be controlled by [uses](Language/uses) blocks that are declared inside pipeline stages. Note that they don't impose any actual constraint on the memory used by tasks that run. They only control concurrency within [uses](Language/uses) blocks that declare resources.

Often it is desirable to make pipelines customizable by exposing variables that the user running the pipeline can set externally. This can be achieved using the -p flag in the form -p `<name>=<value>`. Multiple -p flags can be provided to specify multiple parameters. Parameters may be read from a file with one value per line by specifying a argument starting with '@' followed by the file name. For example, 

```groovy 

bpipe run @params.txt pipeline.groovy
```

The file params.txt should have one option per line, for example:
```groovy 

-p foo=bar
-p baz=fubar
```
