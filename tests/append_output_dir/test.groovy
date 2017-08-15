hello = {
    output.dir = "foo"
    exec """
        touch $output.txt
    """
}

world = {

    println "The output directory is $output.dir"

    output.dir = output.dir + "/bar"

    println "The new output directory is $output.dir"
}

run { 
    hello + world
}

