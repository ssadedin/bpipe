# Executing Inline R Code


### Synopsis

    
    R {"""
        < R code >
    
       """}
    
### Behavior

Executes the embedded `R` code by passing it to the `Rscript` utility. Tokens preceded with `$` that match known Bpipe variables are evaluated as Bpipe variables before passing the script through. Other such tokens are passed through unmodified. This has the effect that you can reference your Bpipe variables such as `$input` and `$output` directly inside your R code.

*Note*: Any variable defined in the Bpipe / Groovy namespace will get evaluated, including pipeline stages, parameters passed to the script, and others. So it is important to understand that this can lead to unexpected evaluation of variables inside the R code. This feature is intended as a simple way to inline small R scripts, for example, to quickly create a plot of some results. Larger R programs should be executed by saving them as files and running them directly using Rscript.

*Note*: By default Bpipe uses the R executable (or actually, the Rscript executable) that it finds in your path. If you want to set a custom R executable, you can do so by adding a bpipe.config file with an entry such as the following:
```groovy 

R {
    executable = "/usr/local/R/3.1.1/bin/Rscript"
}
```

### Examples

**Plot the values from a tab separated file**
```groovy 

R {"""
  values = read.table('$input.tsv');
  png('$output.png')
  plot(values)
  dev.off()
"""}
```
