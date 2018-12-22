hello = {
    exec """
        cp $input.txt $output.csv
    """
}

there = {
    exec """
        cp $input.txt $output.csv
    """
}

world = {
    exec """
        cat $inputs.csv > $output.xml
    """
}

mars = {
    exec """
        cat $inputs.csv > $output.tsv
    """
}



run {
    hello + ['foo','bar','baz'] * [ there ] >>> [world,mars]
}
