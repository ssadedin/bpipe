# The log command

## Synopsis

    
    
        bpipe log [options for tail]
    


## Options

Internally the `log` command actually runs `tail` to display the log.  
You can pass any normal options you would like to the tail command, for example:
```groovy 

bpipe log -n 2000
```

Will display the last 2000 lines of the log instead of the default (which is to fill the screen according to the terminal height).

## Description

Display the log file for the currently running, or most recently run Bpipe job in the local directory.  If the job is running, this command will "tail" the log file using the -f option so that you see a continuous scrolling log.  If it is not finished it will show the trailing lines of the log and exit back to the shell.