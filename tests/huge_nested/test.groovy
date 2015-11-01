hello = {
    exec """
        cp $input.txt $output.csv
    """
}

world = {
    exec """
        cp $input.csv $output.xml
    """
}

there1 = {
    exec """
        cp $input.csv $output.csv
    """
}

there2 = {
    exec """
        cp $input.csv $output.csv
    """
}

there3 = {
    exec """
        cp $input.csv $output.csv
    """
}

there4 = {
    exec """
        cp $input.csv $output.csv
    """
}

there5 = {
    exec """
        cp $input.csv $output.csv
    """
}

there6 = {
    exec """
        cp $input.csv $output.csv
    """
}

there7 = {
    exec """
        cp $input.csv $output.csv
    """
}

there8 = {
    exec """
        cp $input.csv $output.csv
    """
}

run { 
    '%.txt' * [ hello ] + '%.csv'  * [ world + there1 + there2 + there3 + there4, world + there5 + there6 + there7 + there8  ] 
}
