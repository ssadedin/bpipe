# The check statement

### Synopsis

```    

1.    check {
         < statements to validate results >
      } 

2.    check(<check name>) {
         < statements to validate results >
      } 


3.    check {
         < statements to validate results >
      } otherwise {
          <statements to execute on failure >
      }
    
4.    check(<check name>) {
         < statements to validate results >
      } otherwise {
          <statements to execute on failure >
      }


```
    

### Availability

0.9.8.6_beta_2 +  (without `otherwise` clause, 0.9.9.5+).

### Behavior

The *check* statement gives a convenient way to implement validation of a
pipeline's outputs or progress and implement an alternative action if the
validation fails. The `check` clause is executed and any `exec` or other
statements inside are processed. If one of these fails, then the `otherwise`
clause executes.

The *check* statement is stateful. Bpipe remembers the result and does not
re-execute a check unless the input files are updated. Thus it is possible to
implement long-running, intensive tasks to perform checks as just like normal
Bpipe commands, they will not be re-executed if the pipeline is re-executed.
The state is remembered by files that are created in the `.bpipe/checks`
directory in the pipeline directory. Effectively, the created check file is
treated as an implicit output of the `check` clause.

A convenient use of `check` is in conjunction with [succeed](Language/Succeed),
[fail](Language/Fail) and [send](Language/Send) commands.

*Note*:  a check does not have to result in aborting of the pipeline.  You may
choose to do nothing in the otherwise clause of the check (it must still exist
though), in which case the check is merely informational.
Alternatively, the `succeed` command will cause the current branch to terminate
and not produce any output files, but leave other branches running. To fail the
whole pipeline, use the `fail` command.

*Note 2*: due to a quirk of Groovy syntax, the *otherwise* command **must** be
placed on the same line as the preceding curly bracket of the `check` clause.

### Examples

**Check that there are 38 lines in the output file**

check {
    exec """
        [ ` wc -l $output` -eq 38 ]
    """
}


**Check that output file is non-zero length and fail the whole pipeline if it is not**

```groovy 

  check {
        exec "[ -s $output ]"
  } otherwise {
        fail "The output file had zero length"
  }
```

**Check that output file is non-zero length and terminate only this branch if it is not**

```groovy 

  check {
        exec "[ -s $output ]"
  } otherwise {
        succeed "The output file had zero length"
  }
```

**Check that output file is non-zero length and notify by email if it is not**

```groovy 

  check {
        exec "[ -s $output ]"
  } otherwise {
        send "Output file $output had zero size" to gmail
  }
```
