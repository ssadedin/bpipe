hello = {
    exec """
        cp $input.txt $output.csv
    """
}

run {
    ["foo","bar","tree"] * [ hello ]
}
