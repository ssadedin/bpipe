hello = {

    // produce sets the outputs on the stage
    produce(input1.replaceAll('fastq.gz','clean.fastq.gz')) {
        // when double ext used with #, that output set may not be recognised as
        // belonging to same stage
        exec """
            cp $input1.fastq.gz $output1.fastq.gz
        """
    }    
}

run {
    hello
}
