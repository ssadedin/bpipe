load 'external2.groovy'

hello = {
    exec """
        echo "hello $FOO"
    """
}
