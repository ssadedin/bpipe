
hello = {
    produce('test.cov.tsv') {
        exec """
            touch $output.tsv
        """
    }
}

world = {
    exec """
        touch $output.cov
    """
}

there = {
    exec """
        cp $input.cov.tsv $output.txt
    """
}


run { 
    hello  + world + there
}
