# Bpipe Example : Paired End Alignment


Illustrates how to align paired end reads from eg. Illumina
using BWA.

### How to Run

If your input files are named in the form:

```groovy 

  sample_1.fastq.gz
  sample_2.fastq.gz
```

Then you would run the pipeline like this:

    bpipe run pipeline.groovy sample`**`.fastq.gz

### Notes

1. to run this example you need a human reference genome
downloaded and indexed in ~/hg19/gatk.ucsc.hg19.fasta"
or you can change the reference below to point to one
you already have. It needs to be indexed with something
like:

bwa index -a bwtsw ~/hg19/gatk.ucsc.hg19.fasta 

2. the magic $threads variable used below will (by default) use
50% of all the available cores on your computer for each
alignment command (thus taking 100% of available threads). You can 
control it by running using the -n command, eg, to run using 4 cores 
in total:

bpipe run -n 4 pipeline.groovy 

```groovy 

// The reference to use
REF="~/hg19/gatk.ucsc.hg19.fasta"

align_bwa = { 

    // We are going to transform FASTQ into two .sai files and a .bam file
    transform("sai","sai","bam") {

        // Alignment with bwa consists of two separate commands:
        //
        //   1. finding the SA coordinates by running bwa aln
        //   2. converting coordinates to actual read alignments and
        //      matching ends together with bwa sampe

        // Step 1 - run both bwa aln commands in parallel
        multi "gunzip -c $input1.gz |  bwa aln -t $threads $REF - > $output1",
              "gunzip -c $input2.gz |  bwa aln -t $threads $REF - > $output2"

        // Step 2 - bwa sampe
        exec """
            bwa sampe $REF $output1 $output2 $input1.gz $input2.gz | 
            samtools view -bSu - | 
            samtools sort - $output.bam.prefix
        """
    }
}

// Single sample, simple execution, no parallelism
run { align_bwa }

// Multiple samples where file names begin with sample
// name and are separated by underscore from the rest of the 
// file name
// run { "%_**.fastq.gz" * [ align_bwa ] }
```
