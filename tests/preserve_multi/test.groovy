hello = {
    preserve("*.csv","*.xml") {
        exec """
            cp $input.txt $output.csv

            cp $input.txt $output.xml
        """
    }
}

world = {
    exec """
        cp $input.csv $output.tsv

        cp $input.xml $output.xls
    """
}

run {
    hello + world
}
