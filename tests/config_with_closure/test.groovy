hello = {
    exec """
        echo "I will take a lot of time to process: $inputs.csv"
    """
}

run { 
    hello 
}

