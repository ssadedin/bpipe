hello = {
    exec "cp -v $input.txt $output.csv"
}

check_stuff = {
    check {
        exec "echo passing check; true"
    } otherwise {
        println "oh dear the check failed"
    }
}

world = {
    exec "cp -v $input.csv $output.xml"
}



run { hello + check_stuff + world }

