hello = {
    exec """
        echo "hello there" > $output.foo.txt
    """
}

world = {

    exec """
        cat $input.xml | tee  $output.json > $output.csv
    """

    check('hello stuff') {
        exec "false"
    } otherwise {
        println "Failure with $output.csv"
    }
}

run {
    hello + world
}
