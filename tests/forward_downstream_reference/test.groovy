hello = {
    forward('hey.fastq.gz')
}

world = {
    exec """
        cp -v $input.fastq.gz $output.bam
    """
}

run {
    hello + world
}
