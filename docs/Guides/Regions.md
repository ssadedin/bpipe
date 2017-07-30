# Working with Genomic Regions

## Genomic Regions

It is often the case that you wish to run your pipeline but limit it to a
particular genomic interval. For example, it may be that you wish to
parallelize the pipeline over separate parts of the genome, or it might be that
you are only interested in a subset of the genome to begin with.

One way to do this with Bpipe would be to simply pass in a region as a parameter, for example:

```groovy 

bpipe run -p region=chr1:10000-20000 pipeline.groovy ...
```

A problem with this is that some commands won't accept a region given in this
form - they may require a BED file to be supplied instead. On the other hand,
other commands won't accept a BED file and require a genomic region
directly. In general, you might have a mix of these in your pipeline, and
you'll end up creating both a BED file and a region variable. If you're
wanting to parallelize inside Bpipe over these regions it gets more
complicated.

Bpipe simplifies all this by supporting genomic regions and ranges as a first
class citizen in Bpipe pipelines. You can specify a single region to run your
pipeline over with the -L flag on the command line:

```groovy 

bpipe run -L chr1:10000-20000 pipeline.groovy ...
```

This has a few different effects:

1. A variable `$region` is available to your pipeline stages, carrying the value from the command line.
1. A variable `$chr` is available to your pipeline that specifies the chromosome from the region
1. You can reference `$region.bed` to reference the region in the form of a BED file. Bpipe creates this file on the fly for you when it is needed.
1. If you have parallelized over regions within your pipeline (for example, using the [chr](Language/Chr) command), Bpipe will limit the parallelization to the regions you've specified.

The last point might be a little hard to understand, but it is tremendously useful in allowing you to create generic pipelines that can parallelize over the whole genome, while still letting the user limit the pipeline to a specific region at the time of running the pipeline. This is particularly useful during testing, since running 20 parallel paths of your pipeline just to find out if one of them is going to work can be a cumbersome way to debug your pipeline.


## Multiple Regions

What about if your pipeline runs on multiple separate regions?

One thing you can do is specify multiple regions with the -L flag by enclosing
the argument in quotes and separating regions with spaces:

```groovy 

bpipe run -L "chr1:1000-2000 chr2:1000-3000" pipeline.groovy ...
```

If you then reference `$region.bed` in your pipeline, Bpipe will create a temporary BED file containing
those regions, while still making the `$region` variable available with these regions in it.

But what about if you have too many regions for that? You can put your regions into a BED file
and reference that instead:

```groovy 

bpipe run -L my_regions.bed pipeline.groovy ...
```

Now Bpipe will do the opposite: if you reference `$regions` it will parse the BED file and create a 
space-concatenated string of regions to use with command line tools that require that, while giving you back
the BED file if you reference `$regions.bed`.

## Parallelizing Over Genomic Regions

A very common technique in processing genomic data is to operate on 
separate genomic regions in parallel. This can be done very easily by chromosome with Bpipe
using the `chr` function:

```
hello = {
    exec """
        echo "I am processing $chr"
    """
}

merge = {
    echo "I would get all the outputs from all the hello stages"
}

run {
    chr(1..22,'X','Y') * [ hello ] + merge
}
    
```

This will cause 24 parallel invocations of the `hello` stage with the `$chr` variable passed a different chromosome
for each one.


There are some problems with parallelizing using chromosomes, however. One problem is that
they aren't the same size. So your `chr22` branch will probably finish in 1/10th the time that 
your `chr1` branch does. If you allocated equal resources for all the branches, most of your resources will
be sitting idle instead of doing useful work.

So Bpipe lets you split up genomic regions in a more fine-grained fashion.

One way to do it is to declare a genome at the top of your pipeline:

```
genome 'GRCh37'

```

Now you can get Bpipe to split that genome into a fixed number of pieces to process in parallel.
Here we split up GRCh37 into 100 pieces:

```
genome 'GRCh37'

hello = {
    println "I will process $region in chromosome $chr"
}

run {
   GRCh37.split(1..22, 100) * [ hello ]
}
```

Bpipe tries to do this in a sensible way: it will try to make even pieces, and if it can it will make whole
pieces. Some tools might want to receive these regions as a BED file. Bpipe makes it straight forward 
by letting you reference `$region.bed` inside your commands:

```
hello = {
    exec """
        fancy command -L $region.bed $input.vcf
    """
}
```

The command sees a BED file. Where does it come from? Bpipe makes it, on request, just in time when your
command tries to reference it.

You may want to approach the problem simply by chopping the genome into exactly even chunks. Bpipe can do
that with the `partition` command:

```
genome 'hg19'

hello = {
    println "Processing $region"
}

run {
    hg19.partition(4000000) * [ hello ]
}
```

This causes Bpipe to divide the genome into (as it happens) about 783 chunks, each 4Mb in size,
and create a parallel branch for each one. Of course, you may not want 783 parallel 
branches executing at once - but you don't need to worry: Bpipe limits concurrency to the 
value set with the `-n` flag to `bpipe run`, so the parallel paths will queue up to execute.

## Discrete Regions Using BED Files

Finally, you might be wanting to process discrete sets regions such as exome target regions.
In this case the pieces to be parallelized are already in little chunks. Bpipe has a command
to let you simply load a BED file and then use the `split` and `partition` commands on
the regions from there:

```groovy
bed_file = bed("test.bed")

hello = {
    exec """
        echo "Processing $region"
    """
}

run {
    bed_file.split(30) * [ hello ]
}
```

This will group the regions from your BED file into 30 approximately equal sized groups, where 
each group has multiple BED regions in it. Bpipe tries to keep all the groups within a factor
of 2 in size relative to each other. This, however, isn't always achievable while keeping the
regions whole. So by default Bpipe may chop some regions into subregions to try and get a better
balance. If you don't want this to happen, you can stop it by supplying an optional parameter:

```
run {
    bed_file.split(30, allowBreaks:false) * [ hello ]
}
```

Now Bpipe will still create 30 parallel branches with the most even arrangement it can find, but it
won't ever break up a single region for BED file into multiple parts. This option is quite useful
for processing (for example) exome target regions in parallel.

Often one wants to allow for a little additional width either side of each region boundary. You
cand do this by using the `padding` option when loading the BED file:


```groovy
// Add 20 bp upstream and downstream to each region
bed_file = bed("test.bed",padding:20)
```















