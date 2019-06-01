hello = {
    println "The output directory is $output.dir"

    exec "date > $output.txt"

}

run { hello }
