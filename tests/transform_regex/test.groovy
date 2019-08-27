hello = {
    transform("(.*)_R1.fastq.gz","(.*)_R2.fastq.gz") to ("\$1.Aligned.out.bam") { 
        exec """
            cat $input1.gz $input2.gz > $output.bam
        """
    }
}

run {
    hello
}
