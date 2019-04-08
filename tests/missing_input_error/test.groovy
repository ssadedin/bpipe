hello = {
    exec """
        cp -v $input.tsv $output.xml
    """
}

run {
    hello 
}
