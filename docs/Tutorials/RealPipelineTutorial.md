# Example of real Bioinformatics Pipeline

## Starting from a Shell Script

In this tutorial we will develop a Bpipe pipeline script for a realistic (but simplified)  analysis pipeline used for variant calling on NGS data.   In this pipeline the following stages are executed:

- sequence alignment using [bwa](http://sourceforge.net/projects/bio-bwa/files/)
- sorting and indexing output files using [samtools](http://samtools.sourceforge.net/)
- PCR duplicate removal using [Picard](http://sourceforge.net/projects/picard/files/)
- calling variants using [samtools](http://samtools.sourceforge.net/)

For the pipeline to work you will need to have the above tools installed, or you can just follow the tutorial without running the examples.  We assume that the Picard files are in /usr/local/picard-tools/, while for the rest of the tools we just assume they are accessible in the PATH variable.  We assume that the reference sequence to align to is in the local directory, named reference.fa, has been indexed using the `bwa index` command and the reads to align are also in the local directory and named s_1.txt.

To show how we convert the pipeline to a Bpipe script we will start with a bash script that represents the pipeline above:
```groovy 

#!/bin/bash
bwa aln -I -t 8 reference.fa s_1.txt > out.sai 
bwa samse reference.fa out.sai s_1.txt > out.sam 

samtools view -bSu out.sam  | samtools sort -  out.sorted

java -Xmx1g -jar /usr/local/picard-tools/MarkDuplicates.jar \
                            MAX_FILE_HANDLES_FOR_READ_ENDS_MAP=1000\
			    METRICS_FILE=out.metrics \
			    REMOVE_DUPLICATES=true \
			    ASSUME_SORTED=true  \
			    VALIDATION_STRINGENCY=LENIENT \
			    INPUT=out.sorted.bam \
			    OUTPUT=out.dedupe.bam 

samtools index out.dedupe.bam 

samtools mpileup -uf reference.fa out.dedupe.bam | bcftools view -bvcg - > out.bcf
```

### Step 1 - Convert Commands to Bpipe Stages

To start out, all we need to do is decide which commands belong together logically and declare Bpipe stages for them.  Inside each stage we will turn the original shell commands into Bpipe "exec" statements.  Here is how it looks:

```groovy 

align = {
    exec "bwa aln -I -t 8 reference.fa s_1.txt > out.sai"
    exec "bwa samse reference.fa out.sai s_1.txt > out.sam"
}

sort = {
    exec "samtools view -bSu out.sam  | samtools sort -  out.sorted"
}

dedupe = {
    exec """
      java -Xmx1g -jar /usr/local/picard-tools/MarkDuplicates.jar 
                            MAX_FILE_HANDLES_FOR_READ_ENDS_MAP=1000
			    METRICS_FILE=out.metrics 
			    REMOVE_DUPLICATES=true 
			    ASSUME_SORTED=true  
			    VALIDATION_STRINGENCY=LENIENT 
			    INPUT=out.sorted.bam 
			    OUTPUT=out.dedupe.bam 
    """
}

index = {
    exec "samtools index out.dedupe.bam"
}

call_variants = {
    exec "samtools mpileup -uf reference.fa out.dedupe.bam | bcftools view -bvcg - > out.bcf"
}

```

*Note: in the original bash script the multiline command for running `MarkDuplicates` needed to have backslashes at the end of each line, however because Bpipe understands multiline commands natively we do not need to include those in the Bpipe version*

### Step 2 - Add Pipeline Definition and Run Pipeline

In Step 1 we defined our pipeline stages but we did not define how they link together.  The following lines added at the bottom of our file define our pipeline:
```groovy 

Bpipe.run {
    align + sort + dedupe + index + call_variants
}
```

So far we have not done very much, but it is worth noting that even if we do nothing more we already have a functional Bpipe script that has many advantages over the old shell script. If you save it as pipeline.txt then you can run it using Bpipe:
```groovy 

bpipe run pipeline.txt
```

If the pipeline works you will get some output like so:
```groovy 

### ==============================================================================================

|                                 Starting Pipeline at 2011-10-06                                  |
### ==============================================================================================

### ======================================== Stage align =========================================

[17bp reads: max_diff = 2
[bwa_aln](bwa_aln]) 38bp reads: max_diff = 3
...
```

Although it looks like the alignment is running in the foreground, actually Bpipe started it in the background: you can hit Ctrl-C at this point and Bpipe will give you the option to terminate the job or leave it running:
```groovy 

Pipeline job running as process 33202.  Terminate? (y/n): 
```

If you answer no the job will keep running, even if you log out.  You can type `bpipe jobs` to see that it is still running, or `bpipe log` to resume seeing the output.

### Step 3 - Define Inputs and Outputs and add Variables

So far our pipeline runs but we aren't getting all the benefits of Bpipe because we have not defined our inputs and outputs in terms of Bpipe's *input* and *output* variables which allow Bpipe to add many useful features.   To add them we literally replace the inputs and outputs with $input and $output.  We also take the opportunity to make our script a little bit more general by extracting out the location of the reference sequence and Picard Tools as variables.

Our pipeline now looks like this:
```groovy 

REFERENCE="reference.fa"
PICARD_HOME="/usr/local/picard-tools/"

align = {
    exec "bwa aln -I -t 8 $REFERENCE $input > ${input}.sai"
    exec "bwa samse $REFERENCE ${input}.sai $input > $output"
}

sort = {
    exec "samtools view -bSu $input  | samtools sort -o - - > $output"
}

dedupe = {
    exec """
      java -Xmx1g -jar $PICARD_HOME/MarkDuplicates.jar           
                            MAX_FILE_HANDLES_FOR_READ_ENDS_MAP=1000
			    METRICS_FILE=out.metrics 
			    REMOVE_DUPLICATES=true 
			    ASSUME_SORTED=true  
			    VALIDATION_STRINGENCY=LENIENT 
			    INPUT=$input 
			    OUTPUT=$output
    """
}

index = {
    exec "samtools index $input"
    forward input
}

call_variants = {
    exec "samtools mpileup -uf $REFERENCE $input | bcftools view -bvcg - > $output"
}

Bpipe.run {
    align + sort + dedupe + index + call_variants
}
```

Now our pipeline runs but Bpipe is managing the names of the input and output files.  This important property means that we have much more flexibility in how our pipeline works.  For example, if you decided to remove the dedupe stage you could just delete it from your pipeline definition - and your pipeline would still work!  

Since the first pipeline stage now expects an input, you must provide one when you run bpipe like so:
```groovy 

bpipe run pipeline.txt s_1.txt
```

_Note:  in the first command executed the $input variable is used in the form  ${input}.sai rather than $input.sai.   This is because in Groovy the "." is a special character (an operator) that accesses a property of a variable.  Just like in Bash scripting when you have this kind of problem, the solution is to surround the actual variable with curly braces to distinguish it from neighboring characters.

_You might notice the index stage has a "forward input" statement at the end. This is because the output of the index stage (an index of the BAM file) is unlikely to be the correct input for a following stage. The "forward" command allows a stage to suggest what outputs are likely to be the best default inputs for the next pipeline stage to use - in this case, it is suggesting that the inputs (a BAM file) should carry through to the next pipeline stage as the default inputs. In the next stage really wanted to consume the BAM file index, it could do so using a [from](Language/From) statement or by using Bpipe's support for input file extensions (eg. by referring to it as "$input.bai").  

### Step 4 - Name Outputs

Our script has one deficiency: the names of the output files are not very satisfactory because they do not have the conventional file extensions. For example, the sam file will be called `s_1.txt.align`.  This happens because we did not give Bpipe any information about what kind of file comes out of the *align* stage.  To make things work more naturally we can give Bpipe some hints about how to name things, by adding [file extensions](Language/ExtensionSyntax) to the `$input` and `$output` variables:
```groovy 

PICARD_HOME="/usr/local/picard-tools/"
REFERENCE="reference.fa"
align = { 
         exec """ 
          bwa aln $REFERENCE $input > $output.sai;
          bwa samse $REFERENCE $output.sai $input > $output.sam
         """ 
}

sort = { 
    exec "samtools view -bSu $input.sam | samtools sort -o - - > $output.bam"
}

dedupe = { 
    exec """ 
           java -Xmx1g -jar $PICARD_HOME/MarkDuplicates.jar
                            MAX_FILE_HANDLES_FOR_READ_ENDS_MAP=1000
                            METRICS_FILE=out.metrics
                            REMOVE_DUPLICATES=true
                            ASSUME_SORTED=true
                            VALIDATION_STRINGENCY=LENIENT
                            INPUT=$input.bam
                            OUTPUT=$output.bam
    """ 
}

index = { 
    exec "samtools index $input.bam"
}

call_variants = { 
    exec "samtools mpileup -uf $REFERENCE $input.bam | bcftools view -bvcg - > $output.vcf"
}

run {
    align + sort + dedupe + index + call_variants
} 
```

Here we have added file extensions to the input and output variables that tell Bpipe what kind of operation is happening in each stage.  Bpipe thinks about operations in two categories: *Transform* operations are producing a new type of file from the input - they modify the file extension to a new one.  *Filter* operations modify a file without changing its type - they keep the file extension the same but add a component to the body of the name. Bpipe infers what is happening by looking at the file extensions. Bpipe allows you to declare the type of operation explicitly if you wish - see   *Transform* or *Filter* annotations, however Bpipe will infer what is happening in most cases just from you supplying file extensions to your `$input` and `$output` variables. What is really imporant here, is that we are not dictating the *full* name of the file to Bpipe, we're just helping it to understand how the *form* of the file name should change.  This means our pipeline is flexible, and no longer uses hard coded file names, but we have sensible recognizable names for our files.
