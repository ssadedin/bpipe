hello = {
    exec """
        cp $input.foo.txt $output.csv
    """
}

run { hello }
