hello = {
    transform('.csv') to('.xml') {
        exec "cp $input.csv $output.xml"
    }
}

world = {
    transform('*.fastq.gz','.xml')  to('_fastqc.zip','.tsv')  {
        exec """
            cat $input1.gz > $output1.zip

            cat $input2.gz > $output2.zip

            touch $output.tsv
        """
    }
}

run { hello + world }
