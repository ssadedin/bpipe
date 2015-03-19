# The Filter Statement


### Synopsis

    
    filter(<filter name>) {
        < statements to filter inputs >
    
    }
### Behavior

Filter is a convenient alias for [produce](Language/Produce) where the name of the output or outputs is deduced from the name of the input(s) by keeping the same file extension but adding a new tag to the file name.   For example, if you have a command that removes comment lines from a CSV file *foo.csv*, you can easily declare a section of your script to produce output *foo.nocomments.csv* by declaring a filter with name "nocomments".  In general you will use filter where you are keeping the same format for the data but performing some operation on the data.

The output(s) that are automatically deduced by *filter* will inherit all the behavior implied by the [produce](Language/Produce) statement.

### Annotation

You can also declare a whole pipeline stage as a filter by adding the Filter annotation prior to the stage in the form `@Filter(<filter name>)`.

### Examples

**Remove Comment Lines from CSV File**
```groovy 

filter("nocomments") {
  exec """
    grep -v '^#' $input > $output
  """
}
```
