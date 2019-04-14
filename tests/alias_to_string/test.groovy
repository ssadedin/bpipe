hello = {
    alias input.txt to 'foo.bar.csv'
}

world = {
    exec """
        cp $input.csv $output.xml
    """
}

run {
    hello + world
}
