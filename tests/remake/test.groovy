
hello = {
    exec """
        echo hello >> $output.txt
    """
}

there = {
    exec """
        cat $input.txt >> $output.csv
    """
}


world = {
    exec """
        cat $input.csv >> $output.xml
    """
}


run {
    hello + there + world
}

