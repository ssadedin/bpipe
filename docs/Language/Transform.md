# The Transform Statement

    
    transform(<transform name>) {
        < statements to transform inputs >
    
    }

    
    transform(<input file pattern>,...) to(replacement pattern, ...)  {
        < statements to transform inputs >
    
    }

    
### Behavior

Transform is a wrapper for [produce](Language/Produce) where the name of the
output or outputs is automatically deduced from the name of the input(s) by modifying the
file extension.   For example, if you have a command that converts 
a CSV file called *foo*.csv to an XML file, you can easily declare a section of your script
to output *foo*.xml using a transform with the name 'xml'.

The output(s) that are automatically deduced by *transform* will inherit all
the behavior implied by the [produce](Language/Produce) statement.

Since version 0.9.8.4, transform has offered an extended form that allows you
to do more than just replace the file extension. This form uses two parts,
taking the form: 
  
  `transform(<input file pattern>) to(<output file pattern>`) { ... }

If the the pattern is not a regular expression or is a regex that has no groups, the input 
and output patterns are assumed to match to the end of the file name. For full 
flexibility, full regular expression syntax can be used to match the inputs, and the
matched groups can be referred to in the output. Although Bpipe will infer a regular expression
for some patterns automatically, this syntax is most safely invoked by adding `~` in
front of the pattern to pre-compile it as a regular expression (which is standard
Groovy syntax). For example:  

  `transform(~'(.*).([0-9]).txt') to(...)`

When groups are specified in the transform source regular expression, they are available
to use in constructing the output name in the `to(...)` clause as `$1`, `$2` etc.

*Note*: input file patterns that contain no regular expression characters, or
that end in "." followed by plain characters are treated as file extensions.
ie: ".xml" is treated as literal ".xml", not "any character followed by xml".

*Note*: when the form '*.ext' is used, all inputs with the given extension are 
matched and their transforms become expected outputs. By contrast, the form
'.ext' or 'ext' causes only the first input matching the extension '.ext' to 
generate an expected output. Specifying a single extension multiple times 
does not map multiple inputs with that extension. Instead use the wildcard
form to match multiple inputs.

### Annotation

You can also declare a whole pipeline stage as a transform by adding the
Transform annotation prior to the stage in the form `@Transform(<filter
name>)`. This form is a bit less flexible, but more concise when you
don't need the flexibility.

### Examples

**Remove Comment Lines from CSV File**
```groovy 

transform("xml") {
  exec """
    csv2xml $input > $output
  """
}
```

**Run FastQC on a Gzipped FASTQ file**

Fastqc produces output files following an unusual convention for naming. To
match this convention, we can use the extended form of transform:

```groovy 

fastqc = {
    transform('.fastq.gz') to('_fastqc.zip') {
        exec "fastqc -o . --noextract $inputs"
    }
    forward input
}
```

Note also that since the output zip files from FastQC are usually not used
downstream, we forward the input files rather than the default of letting the
output files be forwarded to the next stage.

**Gunzip Two Files Using a Wildcard Pattern**

```groovy 
    transform('*.fastq.gz') to('.fastq') {
        exec """
            gunzip -cf $input1.fastq.gz > $output1.fastq

            gunzip -cf $input2.fastq.gz > $output2.fastq
        """
    }
```

Here the pattern sets a rule for all input files ending with `.fastq.gz`, which 
are all mapped so that .fastq.gz is replaced with .fastq.

**Accept Either TSV or CSV and Transform to XLSX**

Some of the more challenging scenarios with transforms arise when the 
inputs can have flexible types. This example shows how you can accept 
multiple file extensions as input to a command. Note that the output 
file name utilises the regular expression group to name the output appropriately:


```groovy
    transform(~'(.*)(.csv|.tsv)') to('$1.xlsx') {
        exec """
            convert $input $output.xlsx
        """
    }
```





