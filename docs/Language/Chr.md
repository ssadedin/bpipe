# The chr statement

### Synopsis

    
    
      chr(<digit|letter> sequence, <digit|letter> sequence) * [ <stage1> + <stage2> + ..., ... ]
    

### Behavior

The `chr` statement splits execution into parallel paths based on chromosome. For the general case, all the files supplied as input are forwarded to every set of parallel stages supplied in the following list. (See below for an exception to this).

The stages that are executed in parallel receive a special implicit variable, 'chr' that can be used to reference the chromosome that should be processed. This is typically passed through to tools that can confine their operations to a genomic region as an argument.

*Note*: Bpipe doesn't do anything to split the input data by chromosome: all the receiving stages get the whole data set. Thus for this to work, the tools that are executed need to support operations on genomic subregions.

### = File Naming Conventions =

When executing inside a chromosomal specific parallel branch of the pipeline, output files are automatically created with the name of the chromosome embedded. This ensures that different parallel branches do not try to write to the same output file. For example, instead of an output file being named "hello.txt" it would be named "hello.chr1.txt" if it was in a parallel branch processing chromosome 1.

If files that are supplied as inputs contain chromosomal segments as part of their file name then the inputs are filtered so that only inputs containing the corresponding chromosome name are forwarded to the parallel segment containing that name. This behavior can be disabled by adding `filterInputs: false` as an additional argument to `chr`.

### Examples

** Call variants on a bam file of human reads from each chromosome in parallel **
```groovy 

gatk_call_variants = {
      exec """
        java -Xmx5g -jar GenomeAnalysisTK.jar -T UnifiedGenotyper
            -R hg19.fa
            -D dbsnp_132.hg19.vcf
            -glm BOTH
            -I $input.bam
            -stand_call_conf 5
            -stand_emit_conf 5
            -o $output.vcf
            -metrics ${output}.metrics
            -L $chr
        """
    }
}

chr(1..22,'X','Y') * [ gatk_call_variants ]
```
