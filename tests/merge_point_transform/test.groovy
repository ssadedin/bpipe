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
    transform('xml') {
        exec """
            cat $inputs.csv > $output.xml
        """
    }
}

run {
    hello + ['foo','bar','baz'] * [ there ] >>> world
}
