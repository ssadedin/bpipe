hello = {
    exec """
        echo "The hello foo is:${input.txt.optional.withFlag('--foo')}"
    """
}

there = {
    exec """
        echo "The there foo is:${input.csv.optional.flag('--foo')}"
    """
}

world = {
    exec """
        echo "The world foo is:${input.txt.withFlag('--foo')}"
    """
}


run { 
    hello + there + world
}
