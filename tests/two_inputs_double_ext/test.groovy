
hello = {
   transform(".bam") to("_R1.fastq.gz", "_R2.fastq.gz") {
       exec """
        echo file1 > $output1 

        echo file2 > $output2

       """
   }
}

world = {
    exec """
        cat $input1.fastq.gz $input2.fastq.gz > $output.txt
    """
}

run {
    hello + world
}
