# Prefix Convenience Function

## Prefix Convenience Function

Some tools don't ask you for an output file directly, but rather they ask you for a *prefix* for the output file, to which they append a suffix.  For example, the `samtools sort` command requires you to give it the name of the output without the ".bam" suffix. To make this easy to deal with, Bpipe offers a "magic" ".prefix" extension that lets you refer to an output, but pass to the actual command the output trimmed of its suffix.

## Example

```groovy 

  sort_reads = {
     filter("sorted") {
         exec "samtools sort -b test.bam $output.prefix"
     }
  }
```

Here we are referring to the output "something.sorted.bam" but only passing "something.sorted" to the actual command.

The "prefix" function actually works on any string (or text) value in Bpipe scripts. So you can even write it like this:
```groovy 

    BASE_FILENAME="test.bam".prefix
```

**Note:** when you use an expression like `$output.prefix`, it is important to understand that Bpipe considers this a reference to the file in $output, not a reference to a file named as the value that `$output.prefix` evaluates to. This means that if your command does not actually create the file with the name that `$output` evaluates to, you may get an error reported by Bpipe, because this is what it is expecting. For this reason, take care when using the `prefix` construct in conjunction with output variables and use them only when your command will actually create a file with the value of `$output` after being passed a truncated file name, and not simply as a convenience to make a string / text value with the value of `$output` truncated.  
