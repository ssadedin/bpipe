hello = {
    exec """
        echo "Hello world $FOO"
    """
}

run {
    hello
}
