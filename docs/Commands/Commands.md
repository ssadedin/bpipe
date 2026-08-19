#Bpipe without any arguments will output a summary of the valid commands.

```
Bpipe Version 0.9.9.4   Built on Sat Jun 10 07:39:02 CDT 2017

usage: bpipe [run|test|debug|execute] [options] <pipeline> <in1> <in2>...
             retry [test]
             stop
             history
             log
             jobs
             checks
             status
             cleanup
             query
             preserve
             diagram <pipeline> <in1> <in2>...
             diagrameditor <pipeline> <in1> <in2>...
 -b,--branch <arg>                comma-separated list of branches to
                                  limit execution to
 -d,--dir <arg>                   output directory
 -f,--filename <arg>              output file name of report
 -h,--help                        usage information
 -l,--resource <resource=value>   place limit on named resource
 -L,--interval <arg>              the default genomic interval to execute
                                  pipeline for (samtools format)
 -m,--memory <arg>                maximum memory
 -n,--threads <arg>               maximum threads
 -p,--param <param=value>         defines a pipeline parameter, or file of
                                  parameters via @<file>
 -r,--report                      generate an HTML report / documentation
                                  for pipeline
 -R,--report <arg>                generate report using named template
 -t,--test                        test mode
 -u,--until <arg>                 run until stage given
 -v,--verbose                     print internal logging to standard error
 -y,--yes                         answer yes to any prompts or questions
```

