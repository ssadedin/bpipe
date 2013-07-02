
@filter("wide")
hello = {
    exec """
        cp test.bed $output.bed 
    """
}

@transform("fasta") 
world = {
    exec """
        cp $input.bed $output.fasta
    """
}

@filter("extracted")
mars = {
    exec """
        cp $input.bam $output.bam
    """
}

align = {
    exec """
        cp $input.bam $output.sai
    """
}

run { 
    [ 
        hello + world, 
        mars 
    ] + align 
}

