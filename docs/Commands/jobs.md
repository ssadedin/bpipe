## The jobs command

## Synopsis

    bpipe jobs [options]

### Description

Display a list of currently running and recently completed Bpipe jobs.

### Options

    -all        Show all jobs, including completed ones
    -age <n>    Show jobs up to n hours old (default: 24)
    -m <str>    Match given substring on directory name
    -watch      Show continuously updated display
    -sleep <n>  Sleep time in ms when watching continuously (default: 10000)
    -u          Only show each directory once with latest result

### Environment Variables

    BPIPE_DB_DIR    Directory where Bpipe stores its database of jobs and logs
                    (default: ~/.bpipedb)

### Examples

    # Show all jobs
    bpipe jobs -all
    
    # Show jobs from last week
    bpipe jobs -age 168
    
    # Show only jobs from directories containing "analysis"
    bpipe jobs -m analysis
    
    # Monitor jobs continuously
    bpipe jobs -watch
    
    # Use custom database location
    export BPIPE_DB_DIR=/data/bpipe/db
    bpipe jobs
