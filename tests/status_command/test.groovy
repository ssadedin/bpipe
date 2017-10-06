

hello = {
    exec """
        echo "Hello there, I am sleeping for 5 seconds"

        sleep 5
    """
}

run { hello }
