hello = {
    exec """
        cp $input.txt ${output("output.tsv")}
    """
}

run { hello }
