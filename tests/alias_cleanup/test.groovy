hello = {
    alias input.txt to output.csv
}

world = {
    exec """
        cp $input.csv $output.xml
    """
}

there = {
    cleanup "*.csv"
}

run {
    hello + world + there
}
