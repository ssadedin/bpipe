hello = {
    branch.dir = "foo"
    exec """
        touch $output.csv
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
