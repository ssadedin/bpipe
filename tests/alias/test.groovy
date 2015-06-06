hello = {
    alias input.txt to output.csv
}

world = {
    exec """
        cp $input.csv $output.xml
    """
}

run {
    hello + world
}
