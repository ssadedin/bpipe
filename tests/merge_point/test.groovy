hello = {
    exec """
        cp -v $input.txt $output.csv
    """
}

there = {
    exec """
        cp -v $input.txt $output.csv
    """
}

world = {
    exec """
        cat $inputs.csv > $output.xml
    """
}

run {
    hello + ['foo','bar','baz'] * [ there ] >>> world
}
