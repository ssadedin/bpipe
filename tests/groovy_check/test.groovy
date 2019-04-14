hello = {
    var a : 5

    exec """
        echo "Running command"

        date > $output.txt
    """

    check {
        groovy """
            println "Running check"

            assert $a > 2
        """
    } otherwise {
        println "Hmmm $a <= 2"
    }
}

run {
    hello
}
