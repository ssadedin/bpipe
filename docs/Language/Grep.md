# The grep statement

### Synopsis

    
    
        grep(<regular expression>) {
          < statements that execute for each line matching expression >
        }
    

or

    
    
        grep(<regular expression>)
    

### Behavior

The *grep* statement is an internal convenience function that processes the input file line by line for each line matching a given regular expression.  

In the first form, the body is executed for each line in the input file that matches and an implicit variable *line* is defined.  The body can execute regular commands using [exec](Language/Exec) or it can use native Groovy / Java commands to process the data.  An implicit variable *out* is defined as an output stream that can write to the current output file, making it convenient to use *grep* to filter and process lines and write extracted data to the output file.

In the second form *grep* works very much like the command line grep with both the input and output file taken from the *input* and *output* variables.   In this case, all matching lines from the input are written to the output.

### Examples

**Remove lines containing INDEL from a file**
```groovy 

grep(".*") {
  if(line.indexOf("INDEL") < 0)
    out << line
}
```

**Create output file containing only INDELS from input file**
```groovy 

grep("INDEL")
```
