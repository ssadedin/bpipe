hello = {
    branch.dir = "foo"
    exec """
        cp $input.txt $output.csv
    """
}

world = {
    exec """
        cp $input.csv $output.xml
    """
}

run {
    ["mybranch"] * [ hello + world ]
}
