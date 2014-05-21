hello = {
    exec """
        cp $input.txt $output.foo.csv
    """
}

run {
    hello
}
