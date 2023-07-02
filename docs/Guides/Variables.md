## Variables in Bpipe

Bpipe supports variables inside Bpipe scripts.  In Bpipe there are two kinds of variables: 

- *implicit* variables  - variables defined by Bpipe for you
- *explicit* variables  - variables you define yourself

### Implicit variables

Implicit variables are special variables that are made available to your Bpipe pipeline stages automatically.   The two important implicit variables are:

- input - defines the name(s) of files that are inputs to your pipeline stage
- output - defines the name(s) of files that are outputs from your pipeline stage
- branch - when a pipeline is running parallel branches, the name of the current branch is available in a *$branch* variable.
- threads - a special variable that causes Bpipe to automatically estimate and assign a suitable
            number of threads to a command
- memory - a special variable that takes on the value of the memory value assigned in
           `bpipe.config`, and if referenced, also triggers Bpipe to throttle the command it
           is referenced in according to resource limit set for the pipeline (eg: using 
           `bpipe run -m`)

The input and output variables are how Bpipe automatically connects tasks together to make a pipeline.  The default input to a stage is the output from the previous stage. In general you should always try to use these variables instead of hard coding file names into your commands.   Using these variables ensures that your tasks are reusable and can be joined together to form flexible pipelines.  

### Extension Syntax for Input and Output Variables

Bpipe provides a special syntax for easily referencing inputs and outputs with
specific file extensions. See [ExtensionSyntax](/Guides/ExtensionSyntax) for
more information.

**Multiple Inputs**

Different tasks have different numbers of inputs and outputs, so what happens
when a stage with multiple outputs is joined to a stage with only a single
input?  Bpipe's goal is to try and make things work no matter what stages you
join together.  To do this:

- The variable *input1* is always a single file name that a task can read
  inputs from.  If there were multiple outputs from the previous stage, the
  *input1* variable is the first of those outputs.  This output is treated as the
  "primary" or "default" output.  The variable `$input` also evaluates, by
  default, to the value of *input1*.
- If a task wants to accept more than one input then it can reference each
  input using the variables *input1*, *input2*, etc.
- If a task has an unknown number of inputs it may reference the variable
  *input* as a *list* and use array like indices to access the elements, for
  example `input[0]` is the same as `input1`, `input[1]` corresponds to
  `input2`, etc.   You can reference the size of the inputs using `inputs.size()`
  and iterate over inputs using a syntax like

```groovy 

  for(i in inputs) {
    exec "... some command that reads $i ..."
  }
```

- When a stage has multiple outputs, the first such output is treated as the
  "primary" output and appears to the next stage as the "input" variable.  If the
  following stage only accepts a single input then its input will be the primary
  output of the previous stage.

- Use of [ExtensionSyntax](/Guides/ExtensionSyntax) will filter the inputs to 
  include only those of a particular type. For example, `$inputs.bam` will evaluate
  to all files with extension '.bam', and `$input1.bam` will find the first BAM file 
  output by an upstream stage.


**Optional Inputs**

Sometimes an input might be provided optionally. In that case, you want to use it if it exists,
or possibly use some other value if it doesn't.

The default behavior of Bpipe is that if you reference an input and it can't be resolved, this
generates a pipeline error and aborts the pipeline. However you can flag an input as optional
by adding `optional` directly after the input reference. For example:

```
hello = {
    exec """
        echo "The file $input.txt.optional is optional"
    """
}
```

The expression `$input.optional` evaluates to blank if the input isn't available. This makes it
possible to use it with Groovy expressions:

```
def actualInput = input.txt.optional?:input.csv
```

However a common scenario is that you want to pass an input with a flag to a command, but only
if the input was provided. This can be accomplished by adding `.flag` after the `optional` in the 
input reference:


```
exec """
   some_command ${input.csv.optional.flag('--foo')}
"""
```

This will run `some_command` by itself if no CSV file is in the inputs, but if the CSV file is
available, it will run `some_command --foo <CSV file>`.

**Optional Outputs**

Sometimes an output may only be produced under certain circumstances. This behaviour can be
difficult to deal with in pipelines where you wish to execute deterministic outcomes
with strong checking to ensure correctness, since absence of an output does not by itself
imply a failure of the process to produce it. Nonetheless, there are occasions where this 
is necessary.

Bpipe allows you to specify that an output
is optional by suffixing the `$output` variable with `.optional`. When this 
occurs, Bpipe adds the following behaviour to treatment of the associated command and
output file:

- it will not be considered an error if the file is not created
- if the command associated with the output executes successfully,
  Bpipe will create a metadata properties file indicating this happened
  (stored in `.bpipe/outputs`)
- if the metadata file exists, commands such as `bpipe retry` etc will
  behave as if the output had been created (so will not re-execute)
- downstream commands that reference the output as an input will not see the output
  if it does not exist. This may result in an error stating the input is missing if
  it is referenced and no available input can be found.

In general, it is better to avoid optional outputs when it is possible to do so. One 
technique that can be helpful is to always create a second output alongside the 
optional one so that there is always an artefact on the file system to 
verify that the command executed. For example, a useful mechanism can be to 
pipe the output from the command into `tee` with an output file that 
actually captures the output for later reference (even though this will also
be in the bpipe log). For example:

```bash
set -o pipefail

some_command -o $ouptut.tsv.optional 2>&1 | tee $output.txt

```

Note: the `pipefail` here is essential as otherwise the pipe will mask an exit status indicating
an error occurred from the original command.


### Explicit Variables

Explicit variables are ones you define yourself.  These variables are created
inline inside your Bpipe scripts using Java-like (or Groovy) syntax.  They can
be defined inside your tasks or outside of your tasks to share them between
tasks.  For example, here two variables are defined and shared between two
tasks:
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

**NOTE 1**: it is important to understand that variables defined in this way have global scope.
and are also modifiable. This becomes important if you have parallel stages in your pipeline.
Modifications to such variables, therefore, can result in race conditions, deadlocks and all 
the usual ills that befall multithreaded programming. Bpipe will prevent reassignment of
such a global variable once the pipeline starts running. However, the properties and contents
of any data structure referenced may still be modifiable. It is strongly recommended 
that you treat any such variables as constants which you assign once and then reference as read only
variables in the remainder of your script. If you do modify them, it is your responsiblity to
implement thread safety for these variables (for example, using a Java/Groovy `synchronized` 
block).

**NOTE 2**: explicit variables can be assigned inside your own pipeline stages. However in current 
Bpipe they are assigned to the _global_ environment. Thus even though you may assign a variable
inside a pipeline stage it is not private to that pipeline stage. If you wish a variable to be
private to a pipeline stage, prefix it with 'def'. If you want to share it with other pipeline
stages that share the same branch (which will confine it to a single thread as well) you can 
prefix it with 'branch.':

```groovy
    
  align_reads = {

    num_threads=8 // bad idea, this is a global variable!

    def thread_num=8 // good idea, this is private

    branch.thread_num = 8 // good idea if you want the variable to be visible 
                          // to other stages in the same branch

    exec "bwa aln -t $num_threads $REFERENCE_GENOME $input"
  }
``` 

Bpipe will allow new global variables to be assigned inside a pipeline stage,
but then will treat them as unmodifiable, so you will receive an error if you attempt
to modify the variable after it was assigned.

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

See the [exec](/Language/Exec) statement for a longer example of using triple quotes.

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


### Environment Variables

If you reference a variable that has no definition, Bpipe evaluates it to its own name, prefixed by
a $ sign. That is, inside an `exec` command, "$USER" would evaluate to "$USER" if undefined. This has 
the effect that variables are "passed through" as environment variables in commands. For example:

```groovy
    exec """
        echo $USER
    """
```

Even though USER is not defined as a Bpipe variable, this will print the value of the USER
environment variable.  Note that Bpipe does not substitute the value of such a variable at all. The
substitution is done by bash when the command is executed. Hence, if you are running commands using a
computational cluster or similar, the value of the environment variable must be defined for jobs
that run on the node where the command is executed.




