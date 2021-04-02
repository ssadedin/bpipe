trim_fastq = {
    transform("(.*).fastq.gz") to("\$1.trim.fastq.gz") { 
        exec """
            cp -v $input1.fastq.gz $output1.fastq.gz 

            cp -v $input2.fastq.gz $output2.fastq.gz 
        """
    }
}

run {
    trim_fastq
}
