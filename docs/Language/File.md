# The File Statement

    
    file(< file path > )

### Availability

Bpipe version 0.9.8.6

### Behavior

A convenience function that creates a Java [File](http://docs.oracle.com/javase/6/docs/api/java/io/File.html) object for the given value. This is nearly functionally equivalent to simply writing '`new File(value)`', however it also converts the given path to a sane, canonicalised form by default, which the default constructor does not do, so that values such as `file(".").name` produce expected results. Bpipe does not check that the file exists or is a valid path.

### Examples

Pass the full path of the current working directory to a command that requires to know it.
**
```groovy 

hello = {
    exec """
        mycommand -d ${file(".").absolutePath} -i $input.bam -o $output.bam
    """
}
run { hello }
```
