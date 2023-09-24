
println "Global foo = $foo"

hello = {
    exec """
       echo "Foo = $foo"
    """
}

world = {
    exec """
        echo "Default Foo = $foo"
    """
}

run {
    hello + world
}
