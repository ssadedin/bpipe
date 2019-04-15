hello = {
    exec """
        cp $input.txt $output.xml
    """
}

run {
    hello
}
