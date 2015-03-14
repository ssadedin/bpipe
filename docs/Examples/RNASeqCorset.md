# Bpipe Example : RNASeq Analysis Using Trinity, Bowtie and Corset


In this example we make a full pipeline for RNASeq analysis using popular tools such as Bowtie, Trinity and Corset. This example is based on the example pipeline and data provided with the Corset tool. You can find the full details of that example (including data) here:

https://code.google.com/p/corset-project/wiki/Example

### How to Run

Using the example data (see link above) you can run the pipeline like so:

    bpipe run pipeline.groovy `**`.sra

### Notes

You may need to adjust the location of Corset and Trinity in the first two variables defined in the pipeline.

```groovy 

TRINITY="/usr/local/corset/trinityrnaseq_r2013-02-25"
CORSET="/usr/local/corset/corset-0.9"

convert = {
    produce(input.prefix+"_1.fastq",input.prefix+"_2.fastq") {
        exec """
            fastq-dump --split-3 $input.sra
        """
    }
}

trim = {
    filter("p1","u1","p2","u2") {
        exec """
            trimmomatic PE -phred33 $input1.fastq $input2.fastq $output1 $output2 $output3 $output4 LEADING:20 TRAILING:20 MINLEN:50
        """
    }
}

pool = {
    produce("all_1.fastq","all_2.fastq") {
        from("**.p1.fastq") {
            exec """
                cat $inputs.fastq | sed 's/ HWI/\\/1 HWI/g' > $output1
            """
        }

        from("**.p2.fastq") {
            exec """
                cat $inputs.fastq | sed 's/ HWI/\\/2 HWI/g' > $output2
            """
        }
    }
}

trinity = {
    from("all_1.fastq", "all_2.fastq") produce("trinity_out_dir.Trinity.fasta") {
        exec """
            $TRINITY/Trinity.pl --JM 10G --seqType fq --CPU 16 --full_cleanup --left $input1.fastq --right $input2.fastq
        """
    }
}

bowtie_index = {
    transform("1.ebwt", "2.ebwt", "3.ebwt", "4.ebwt") {
        exec """
            bowtie-build $input.fasta $input.fasta.prefix
        """
    }
}

@transform("bam")
bowtie_map = {
    exec """
       bowtie --all -S $input.fasta.prefix -1 ${input.prefix}_1.fastq -2  ${input.prefix}_2.fastq | samtools view -S -b - > $output.bam 
    """
}

corset = {
    exec """
        $CORSET/corset -g 1,1,1,2,2,2 -n A1,A2,A3,B1,B2,B3 $inputs.bam
    """
}

run {
    "%.sra" ** [ convert +  trim ] + pool + trinity + bowtie_index + "%.sra" * [ bowtie_map ] + corset
}

```
