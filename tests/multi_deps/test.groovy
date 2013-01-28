align_bwa = {

    // We are going to turn our FASTQ files into two .sai files and a .bam file
    transform("sai","sai","bam") {
        multi "gunzip -c $input1.gz > $output1",
              "gunzip -c $input2.gz > $output2"

        // The second step is resolving the coordinates and pairing the
        // ends together
        exec """
            cat $output1 $output2 $input1.gz $input2.gz > ${output.bam.prefix}.bam
        """
      
    }

}
  
run { align_bwa }
