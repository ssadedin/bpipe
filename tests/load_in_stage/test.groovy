hello = {
    load 'foo.groovy'
}

world = {
    var message : "This should not execute"

    exec """
        echo "$message"
    """
}

run { [ hello + world ] + world.using(message:"this should execute") }
