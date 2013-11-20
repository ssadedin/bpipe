hello = {
    exec """
        cp $input $output.csv
    """
}

run {
   chr(1..2) * [ hello ]
}
