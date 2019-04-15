hello = {
    exec """
        cp $input.txt $output.csv
    """
}

world = {
    var worldy: false

    if(worldy) {
        exec """
            cp $input.txt $output.xml
        """
    }
}

run {
    hello + world
}

