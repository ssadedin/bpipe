# Working with Genomic Regions

## Genomic Regions

It is often the case that you wish to run your pipeline but limit it to a particular genomic interval. For example, it may be that you wish to parallelize the pipeline over separate parts of the genome, or it might be that you are only interested in a subset of the genome to begin with.

One way to do this with Bpipe would be to simply pass in a region as a parameter, for example:
```groovy 

bpipe run -p region=chr1:10000-20000 pipeline.groovy ...
```

A problem with this is that some commands won't accept a region given in this form - they may require a BED file to be supplied instead. On the other hand, other commands won't accept a BED file and require a genomic region directly. In general, you might have a mix of these in your pipeline, and you'll end up creating both a BED file and a region variable. If you're wanting to parallelize inside Bpipe over these regions it gets more complicated.

Bpipe simplifies all this by supporting genomic regions and ranges as a first class citizen in Bpipe pipelines. You can specify a single region to run your pipeline over with the -L flag on the command line:
```groovy 

bpipe run -L chr1:10000-20000 pipeline.groovy ...
```

This has a few different effects:

1. A variable `$region` is available to your pipeline stages, carrying the value from the command line.
1. A variable `$chr` is available to your pipeline that specifies the chromosome from the region
1. You can reference `$region.bed` to reference the region in the form of a BED file. Bpipe creates this file on the fly for you when it is needed.
1. If you have parallelized over regions within your pipeline (for example, using the [chr](Language/Chr) command), Bpipe will limit the parallelization to the regions you've specified.

The last point might be a little hard to understand, but it is tremendously useful in allowing you to create generic pipelines that can parallelize over the whole genome, while still letting the user limit the pipeline to a specific region at the time of running the pipeline. This is particularly useful during testing, since running 20 parallel paths of your pipeline just to find out if one of them is going to work can be a cumbersome way to debug your pipeline.

*Note*: You can specify multiple regions with the -L flag by enclosing the argument in quotes and separating regions with spaces:
```groovy 

bpipe run -L "chr1:1000-2000 chr2:1000-3000" pipeline.groovy ...
```
