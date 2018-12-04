hello = {
    exec """
        echo run $branch.name

        cp $input.txt $output.csv
    """
}

run {
    bed('test.bed').split(3) * [ hello ]
}
