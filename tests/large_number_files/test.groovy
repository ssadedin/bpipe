split_DB = {
    output.dir = "fasta"
    produce("*.fa") {
        exec """
            for i in {0..1000}; do echo ${i} >  fasta/${i}.fa; done
        """
    }
}

iterative_assembler = {
    exec """
        cat $input.fa > $output.csv
    """
}

run {
     split_DB + "%.fa" * [ 
        iterative_assembler
    ]
}

