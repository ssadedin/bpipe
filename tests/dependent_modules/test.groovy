hello = {
    exec """
        echo '$HELLO $WORLD'
    """
}

run { hello }

