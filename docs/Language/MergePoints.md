# Merge points

## Synopsis

```groovy
   <branch definition> * [ <stage1>,<stage2>,...] >>> <merge stage>
```
    
### Behavior

Defines a stage that is identified as a merge point for a preceding set of parallel stages.
This is nearly the same as using the `+` operator, however it causes Bpipe to name the outputs
of the merge stage differently. Specifically, ordinarily, the merge stage would name
its output according to the first input by default. However this often leads to misleadingly
named outputs that appear to be derived only from the first parallel branch of the parallel segment.
When the mergepoint operator is used, Bpipe will still derive the output name from the first input,
however it will excise the branch name from the file name of that input and replace it with "merge",
so that the output is clearly identified as a merge of previous inputs.

The merge point operator is particularly useful when dynamic branching constructs are used such 
that you cannot anticipate exactly what the branch names will be beforehand.

### Examples

**Merge Outputs from Three Pipeline Branches Together**

Here a pipeline branches three ways with with branches called
`foo`, `bar` and `baz`. If the `>>>` operator was not used, the final output
would end with `foo.there.world.xml`. However because the merge point
operator is applied, the final output ends with `.merge.there.world.xml`

```groovy 

hello = {
    exec """
        cp -v $input.txt $output.csv
    """
}

there = {
    exec """
        cp -v $input.csv $output.tsv
    """
}

world = {
    exec """
        cat $inputs.tsv > $output.xml
    """
}

run {
    hello + ['foo','bar','baz'] * [ there ] >>> world
}
```
**Split hg19 500 ways and merge the results back together**

Note that here we make use of Bpipe's automatic region splitting and magic
`$region.bed` variable.

```
genome 'hg19'

compute_gc_content = {
    exec """gc_content -L $region.bed $input.txt > $output.gc.txt"""
}

calculate_mean = {
    exec """
        cat $inputs.txt | gngs 'println(graxxia.Stats.mean())' > $output.mean.txt
    """
}

run {
    hg19.split(500) [ compute_gc_content ] + calculate_mean
}

```