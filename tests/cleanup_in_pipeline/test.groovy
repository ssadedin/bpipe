hello = {
    exec "cp $input.txt $output.csv"
}

there = {
    exec "cp $input.csv $output.xml"
    println "I'm all done there-ing"
}

cleanup_stuff = {

    println "It's time to clean house!"

    cleanup "*.hello.csv"
}

world = {
    exec "cp $input.xml $output.tsv"
}

run {
    hello + there + cleanup_stuff + world
}

