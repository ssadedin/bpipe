
# The from statement

### Synopsis
    
      from(<pattern>,<pattern>,...|[<pattern1>, <pattern2>, ...]) {
         < statements to process files matching patterns >
      }
    
    
      from(<pattern>,<pattern>,...|[<pattern1>, <pattern2>, ...])  [filter|transform|produce](...) {
         < statements to process files matching patterns >
      }
    

### Behavior

The *from* statement reshapes the inputs to be the most recent output file(s) matching the given pattern for the following block.  This is useful when a task needs an input that was produced earlier in the pipeline than the previous stage, or other similar cases where your inputs don't match the defaults that Bpipe assumes.

Often a *from* would be embedded inside a *produce*, *transform*, or *filter*
block, but that is not required. In such a case, *from* can be joined directly
to the same block by preceding the transform or filter directly with the 'from'
statement.

The patterns accepted by *from* are glob-like expression using `*` to represent
a wildcard. A pattern with no wild card is treated as a file extension, so for
example "`csv`" is treated as "`*.csv`", **but will only match the first (most
recent) CSV file**. By contrast, using `*.csv` directly will cause all
CSV files from the last stage that output a CSV file to match the first
parameter. This latter form is particularly useful for gathering all the files
of the same type output by different parallel stages.

When provided as a list, *from* will accumulate multiple files with different
extensions.  When multiple files match a single extension they are used
sequentially each time that extension appears in the list given. 

### Examples

**Use most recent CSV file to produce an XML file**
```groovy 

  create_excel = {
    transform("xml") {
      from("csv") {
        exec "csv2xml $input > $output"
      }
    }
  }
```

**Use 2 text and CSV files to produce an XML file**
```groovy 

  // Here we are assuming that some previous stage is supplying
  // two text files (.txt) and some stage (possibly the same, not necessarily)
  // is supplying a CSV file (.csv).
  create_excel = {
      from("txt","txt","csv") {
        exec "some_command $input1 $input2 $input3 > $output.xml" // input1 and input2 will be .txt, input3 will be .csv
      }
  }
```

**Match all CSV files from the last stage that produces a XML file**
```groovy 

  from("*.csv") transform("xml") {
        exec "csv2xml $inputs.csv > $output"
  }
```
