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
    filter('hey') {
        exec """
            cat $inputs.csv > $output.csv
        """
    }
}

run {
    hello + ['foo','bar','baz'] * [ there ] >>> world
}
