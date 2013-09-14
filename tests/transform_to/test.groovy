hello = {
    transform('.csv') to('.xml') {
        exec "cp $input.csv $output.xml"
    }
}

world = {
    transform('.fastq.gz')  to('_fastqc.zip')  {
        exec "cat $input.gz > $output.zip"
    }
}

run { hello + world }
