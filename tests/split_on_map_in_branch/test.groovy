
hello = {
    exec """
        cp $input $output
    """
}

world = {
    exec """
        cp $input $output
    """
}

samples = [ 'foo' : 'test.txt' ]
sizes = ["1"]

run {
    sizes * [
        hello  +
        samples * [ world ]
    ]
}
