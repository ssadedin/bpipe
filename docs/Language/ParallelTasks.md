# Running Pipeline Stages in Parallel

A frequent need in Bioinformatics pipelines is to execute several tasks at the same time.  There are two main cases where you want to do this:

1. you have one set of data (eg. a sample) that needs to undergo several independent operations that can be done at the same time
1. your data is made up of many separate samples which can be processed independently through part or all of your pipeline

In both cases you can save a lot of time by doing the operations in parallel instead of sequentially.  Bpipe supports this kind of parallel execution with a simple syntax that helps you declare which parts of your pipeline can execute at the same time and what inputs should go to them.

## Executing Multiple Stages Simultaneously on the Same Data

Suppose you had a very simple "hello world" pipeline as illustrated below:
```groovy 

Bpipe.run {
  hello + world
}
```

Now suppose you wanted to add a second "mars" stage that would execute simultaneously with the "world" pipeline stage.   All you need to do is place all the stages that execute together in square brackets and separate them with commas:
```groovy 

Bpipe.run {
  hello + [ world,mars ]
}
```

_Note: if you are familiar with Groovy syntax, you will notice that the square bracket notation is how you define a list in Groovy.  Thus all we are saying is that if you give Bpipe a list of stages to process, it executes them in parallel._

You can execute multiple stages in parallel too:
```groovy 

Bpipe.run {
  hello + [ blue + world, red + mars ]
}
```

Here "blue + world" and "red + mars" form sub-pipelines that execute in parallel with each other.  You can have more stages at the end that are sequential:
```groovy 

Bpipe.run {
  hello + [ blue + world, red + mars ] + nice_to_see_you
}
```

Note that in this case the last stage `nice_to_see_you` won't execute until all of the preceding stages executing in parallel have finished.   It will receive **all** the outputs combined from both the "blue + world" and "red + mars" stages as input.

You can also nest parallel tasks if you wish:
```groovy 

Bpipe.run {
  hello + [ blue + world, red + [mars,venus] ] + nice_to_see_you
}
```

In this case the `mars` and `venus` stages will execute simultaneously, but only after the `red` stage has finished executing.

## Parallelizing Based on Chromosome

In bioinformatics it is often possible to run operations simultaneously across multiple chromosomes.  Bpipe makes this easy to achieve using a special syntax as follows:
```groovy 

Bpipe.run {
  chr(1..5) * [ hello ]
}
```

This will run 5 parallel instances of the 'hello' pipeline stage, each receiving the same file(s) as input.  Each stage will receive an implicit  `chr` variable that can be used to refer to the chromosome that is to be processed by the stage.  This can be used with many tools that accept the chromosome as an input to specify the region to process.  For example, with samtools:
```groovy 

hello = {
    exec """samtools view test.bam $chr | some_other_tool """
}
```

Multiple ranges or single chromosomes can be specified:
```groovy 

Bpipe.run {
  chr(1..10, 'X','Y') * [ hello ]
}
```

This would run 12 parallel stages, passing 'chr1' through to 'chr10' and 'chrX' and 'chrY' as the the `chr` variable to all the different stages.

_Note_: Bpipe also makes available the `$region` variable. If no genome is declared (see below) Bpipe will emit a region in the form:
`chrN:0-0` for statements inside the pipeline. Many tools accept this as meaning to include the entire chromosome. However if you declare a genome
(see below) then Bpipe will omit a region that includes the entire chromosome with the correct range for that chromosome.

### Alternative Genomes

When parallelizing by chromosome the format of the reference sequence (eg: chromosome) can vary according to which
underlying reference genome is used. The default configuration of Bpipe is that it uses the UCSC style convention
of including 'chr' as a prefix before reference sequences. To omit this, as well as inform Bpipe about other aspects
of the genomes, declare the genome you are using at the start of your pipeline:

```
genome 'GRCh37'
```
For genomes recognized by Bpipe that do not include the 'chr' prefix, Bpipe will use the correct style in 
when evaluating the `$chr` and `$region` variable.

## Executing Multiple Stages Simultaneously on Different Data

In the above examples each parallel stage received the same input files and operated on them together.  Sometimes however what you really 
want is to have each input file or groups of your input files processed independently through the same stage (or stages).   Bpipe calls 
this *input splitting* and gives you a concise and simple way to achieve it.

Suppose we have 10 input files and we want all 10 files named input_1.txt to input_10.txt to be processed at the same time.  Here is how it looks:
```groovy 

Bpipe.run {
   "input_%.txt" * [ hello + world ] + nice_to_see_you
}
```

There are two things to notice here:
1. The pipeline starts with an *input splitting pattern* containing a % character that shows which part of the file name should be used to split the input files into groups
1. The pipeline uses a `**` (or multiplication) operator in your pipeline definition instead of the usual +

Note that Bpipe still requires you to specify the files to match against on the command line when you run your pipeline; the matching is not done on files in the file system, but on files that are part of the pipeline. So if you saved it in a file called 'helloworld.pipe' then you might run this example using something like this:

```groovy 

bpipe run helloworld.pipe input*.txt
```

### Input Splitting Patterns

### Splitting

Bpipe uses a very simple wildcard pattern syntax to let you indicate how your files should be split into groups for processing.  In these patterns you simply replace the portion of file names that indicates what group the file belongs to with the percent character which acts as a wildcard (matches any number of characters).  Files that share the same grouping portion will be passed together to the the parallel pipeline stages to process.

The pattern matching used for grouping files is a substring match.  Therefore you only need to supply enough of the input file name to uniquely identify where the grouping character is.  For example, the following pipeline is equivalent to the one above:
```groovy 

Bpipe.run {
   "_%." * [ hello + world ] + nice_to_see_you
}
```

This means Bpipe will look for the first (and shortest) token in the file name that is flanked by an underscore on the left and a period (. character) on the right.  This may be useful if your files have portions of their names that differ but are not related to how you wish to group them.

*Note: files that mismatch the grouping operator pattern will be filtered out of the inputs altogether.  This feature can be useful by allowing you to have a directory full of files that you provide as input even if some of them are not real input files - Bpipe will filter out only the ones it needs based on the pattern you specified*.

### Ordering

Bpipe supports one other special character in its input splitting patterns:  the `*` wildcard.  This also acts as a wildcard match but it *does not* split the input into groups.  Instead, it affects ordering within the groups that are split.  When Bpipe matches a `*` character in an input splitting pattern it first splits the files into their groups (based on the `%` match) and then sorts them based on the portions that match the `*` character.  This helps you ensure that even after splitting, your files are still in a sensible order.   For example, consider the following input files

- input_1_1.txt
- input_1_2.txt
- input_2_2.txt
- input_2_1.txt

You can split *and* sort the inputs using a pattern:

  `"input_%_*.txt"`

This pattern will then split and order the files like so:

**Group 1**
- input_1_1.txt, input_1_2.txt

**Group 2**
- input_2_1.txt, input_2_2.txt

Notice that the second group had its files reversed in order because Bpipe sorted them.

### Explicitly Specifying Parallel Paths

If you don't get the flexibility you need from the above mechanisms, you can set the branch paths yourself explicitly by specifying a Groovy List or a Map that tells Bpipe what paths you want. When you specify a Map, the keys in the map are interpreted as branch names and the values in the Map are interpreted as files, or lists of files, that are supplied to the branch as input.

For example:
```groovy 

// Create a data structure (Map) that maps branches to files
def branches = [
    sample1: ["sample1_2.fastq.gz"],
    sample2: ["sample2_2.fastq.gz"],
    sample3: ["sample3_2.fastq.gz"]
]

align = {
   // keep the sample name as a branch variable
   branch.sample = branch.name 
   ...
}

run { branches * [ align ] }
```

In this example the `align` stage will run three times in parallel and the files specified for each branch will be explicitly provided to it. Of course, in normal usage this technique would not be best applied by specifying them statically, but rather for when you want to read the information from a file or database or other source and construct the branch => file mapping from that.

### Allocating Threads to Commands

Sometimes you know in advance exactly how many threads you wish to use with a command. In that case, it makes sense
to specify it using the `procs` attribute in a configuration, or to specify it directly in the pipeline stage
with the [Uses](uses) clause.

Other times, however, you want to be more flexible, and allow resources to be assigned more dynamically. This means
that if more compute power is available, you can take advantage of it, and when less is available, your pipeline can
scale down to run on what is available. Bpipe offers some capability for this through the special `$threads` variable.
This variable can behave in two different ways:

- when a `procs` configuration is available from the bpipe.config file, the `$threads` variable will
  take that value.
- when no `procs` configuration is specified by configuration, Bpipe will decide on a sensible value for
  `$threads` based on the currently unutilised concurrency slots specified to Bpipe via the `-n` flag
  (defaulting to the number of cores on the computer Bpipe is running on). Once assigned, `$threads` maps
  to the `procs` configuration value for any commands that it is specified in.

Example:

```
align_bwa = {
  exec "bwa aln -t $threads ref.fa $input.fq"
}
```

Here Bpipe will assign a value to $threads that tries to best utilise the total
available concurrency. For example, if there are 32 cores on the computer you
are using and there are 4 of these stages that execute in parallel, each one
should get allocated 8 cores, as long as they start at approximately the same
time.

### Limiting Dynamic Concurrency

Bpipe implements dynamic concurrency in a somewhat subtle manner. When a command asks
for a value for `$threads`, Bpipe needs to decide what other tasks the current
pool of available threads should be shared with. If it simply calculates this
value immediately then the first command that tries to use $threads will get
allocated all the remaining concurrency slots and others will have none
available. To avoid this, when a command asks for `$threads`, Bpipe pauses the
current branch and waits until all concurrently executing paths in the pipeline
are either executing tasks or have also requested `$threads`. Then the threads
are divided up among all the requestors, and allocated fairly. 


In general, the above process results in a "fair" allocation of threads to
competing tasks, but you should be aware that "greedy" behavior can still
emerge for tasks that are scattered apart in time. For this reason, it can be
useful to set an upper limit on how many threads Bpipe will give to any one
task. You can do this by setting the `max_per_command_threads` variable in
`bpipe.config`. This will limit the total number of threads that can be
allocated.

Another approach to this is to specify thread allocation ranges in the 
configuration of your commands via the `procs` variable. For example, we
can reserve between 2 and 8 threads for bwa in the previous example by 
specifying in bpipe.config:

```
commands {
    bwa {
        procs=2..8
    }
}
```
This will allow Bpipe to set $threads to anything between 2 and 8.


### Limitations

1. When you run stages in parallel, you should always use the Bpipe specified output file (defined for you as the $output variable) rather than hard coding the file names.  This is needed because when you define output files yourself Bpipe detects the creation of the files and interprets them as outputs of whatever pipeline stage is currently executing.  However with multiple stages executing this detection can assign the the output to the wrong pipeline stage or even the wrong parallel instance of the correct pipeline stage.  If you wish to "hard code" the file name that is output from a stage (or part of a stage) you can still do so, but you should do it by wrapping the command that creates that output with a Produce statement, for example:
```groovy 

hello = {
  produce("hello.txt") {
    exec "cp $input $output"
  }
}
```

Even this is not recommended because you may end up overwriting your output files from multiple parallel threads if you are not careful.  In general, whenever you can, let Bpipe manage the names of your files and just give it hints to make them look the way you want.
