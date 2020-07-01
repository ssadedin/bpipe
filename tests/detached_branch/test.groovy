hello = {
    exec """
        cp -v $input.txt $output.xml
    """
}

world = {

    // Thread.sleep(10000)

    println "Starting world ...."


    from('xml', crossBranch:true) {
        exec "cp -v $input.xml $output.csv"
    }
}

run {
    [hello, world]
}
