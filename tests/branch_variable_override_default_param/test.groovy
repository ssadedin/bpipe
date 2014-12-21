hello = {
    branch.foo = "fubar"
}

world = {
    var foo : "fooooooo"

    exec """
        echo foo=$foo
    """
}

run { hello + world }
