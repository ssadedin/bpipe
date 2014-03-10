hello = {
    produce("*.csv") {
        exec """
            touch test.csv

            touch ${output("test.xml")}
        """
    }
}

world = {
    exec """
        cp $input.xml $output.tsv
    """
}

run { hello + world } 
