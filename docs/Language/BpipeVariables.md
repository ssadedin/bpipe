# Variables in Bpipe

Bpipe supports variables inside Bpipe scripts.  In Bpipe there are two kinds of variables: 

- *implicit* variables  - variables defined by Bpipe for you
- *explicit* variables  - variables you define yourself

### Implicit Variables

Implicit variables are special variables that are made available to your Bpipe pipeline stages automatically.   The two important implicit variables are:

- input - defines the name(s) of files that are inputs to your pipeline stage
- output - defines the name(s) of files that are outputs from your pipeline stage
- environment variables - variables inherited from your shell environment (note: this feature is not implemented yet - coming soon!).
- branch - when a pipeline is running parallel branches, the name of the current branch is available in a *$branch* variable.

The input and output variables are how Bpipe automatically connects tasks together to make a pipeline.  The default input to a stage is the output from the previous stage. In general you should always try to use these variables instead of hard coding file names into your commands.   Using these variables ensures that your tasks are reusable and can be joined together to form flexible pipelines.  

### =Extension Syntax for Input and Output Variables=

Bpipe provides a special syntax for easily referencing inputs and outpus with specific file extensions. See [ExtensionSyntax](Language/ExtensionSyntax) for more information.

**Multiple Inputs**

Different tasks have different numbers of inputs and outputs, so what happens when a stage with multiple outputs is joined to a stage with only a single input?  Bpipe's goal is to try and make things work no matter what stages you join together.  To do this:

- The variable *input1* is always a single file name that a task can read inputs from.  If there were multiple outputs from the previous stage, the *input1* variable is the first of those outputs.  This output is treated as the "primary" or "default" output.  The variable `$input` also evaluates, by default, to the value of *input1*.
- If a task wants to accept more than one input then it can reference each input using the variables *input1*, *input2*, etc.
- If a task has an unknown number of inputs it may reference the variable *input* as a *list* and use array like indices to access the elements, for example `input[0]` is the same as `input1`, `input[1]` corresponds to `input2`, etc.   You can reference the size of the inputs using `input.size()` and iterate over inputs using a syntax like
```groovy 

  for(i in input) {
    exec "... some command that reads $i ..."
  }
```

- When a stage has multiple outputs, the first such output is treated as the "primary" output and appears to the next stage as the "input" variable.  If the following stage only accepts a single input then its input will be the primary output of the previous stage.

### Explicit Variables

Explicit variables are ones you define yourself.  These variables are created inline inside your Bpipe scripts using Java-like (or Groovy) syntax.  They can be defined inside your tasks or outside of your tasks to share them between tasks.  For example, here two variables are defined and shared between two tasks:
```groovy 

  NUMTHREADS=8
  REFERENCE_GENOME="/data/hg19/hg19.fa"

  align_reads = {
    exec "bwa aln -t $NUMTHREADS $REFERENCE_GENOME $input"
  }
  
  call_variants = {
    exec "samtools mpileup -uf $REFERENCE_GENOME $input > $output"
  }
```

### Variable Evaluation

Most times the way you will use variables is by referencing them inside shell commands that you are running using the *exec* statement.  Such statements define the command using single quotes, double quotes or triple quotes.  Each kind of quotes handles variable expansion slightly differently:

**Double quotes** cause variables to be expanded before they are passed to the shell.  So if input is "myfile.txt" the statement: 
```groovy 

  exec "echo $input"
```

will reach the shell as exactly:
```groovy 

  echo myfile.txt
```

Since the shell sees no quotes around "myfile.txt" this will fail if your file name contains spaces or other characters treated specially by the shell.  To handle that, you could embed single quotes around the file name:
```groovy 

  exec "echo '$input'"
```

**Single quotes** cause variables to be passed through to the shell without expansion.  Thus 
```groovy 

  exec 'echo $input'
```

will reach the shell as exactly:
```groovy 

  echo $input
```

**Triple quotes** are useful because they accept embedded newlines.  This allows you to format long commands across multiple lines without laborious escaping of newlines.   Triple quotes escape variables in the same way as single quotes, but they allow you to embed quotes in your commands which are passed through to the shell.  Hence another way to solve the problem of spaces above would be to write the statement as:
```groovy 

  exec """
    echo "$input"
  """
```

See the [exec](Language/Exec) statement for a longer example of using triple quotes.

### Referencing Variables Directly

Inside a task the variables can be referenced using Java-like (actually Groovy) syntax.  In this example Java code is used to check if the input already exists:
```groovy 

  mytask = {
      // Groovy / Java code!
      if(new File(input).exists()) {
        println("File $input already exists!")
      }
  }
```

You won't normally need to use this kind of syntax in your Bpipe scripts, but it is available if you need it to handle complicated or advanced scenarios.

### Differences from Bash Variable Syntax

Bpipe's variable syntax is mostly compatible with the same syntax in languages like Bash and Perl. This is very convenient because it means that you can copy and paste commands directly from your command line into your Bpipe scripts, even if they use environment variables.

However there are some small differences between Bpipe variable syntax and Bash variable syntax:

- Bpipe always expects a `$` sign to be followed by a variable name.  Thus operations in Bash that use `$` for other things need to have the `$` escaped so that Bpipe does not interpret it.  For example:

```groovy 

  exec "for i in $(ls **.bam); do samtools index $i; done"
```

In this case the $ followed by the open bracket is illegal because Bpipe will try to interpret it as a variable.  We can fix this with a backslash:
```groovy 

  exec "for i in \$(ls **.bam); do samtools index $i; done"
```

- Bpipe treats a `.` as a special character for querying a property, while Bash merely delimits a variable name with it. Hence if you wish to write `$foo.txt` to have a file with '.txt' appended to the value of variable foo, you need to use curly braces: `${foo}.txt`.
