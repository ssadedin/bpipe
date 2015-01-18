hello = {
    exec "cp $input.txt $output.csv"
}

there = {
    exec "cp $input.csv $output.xml"
}

cleanup_stuff = {
    cleanup "*.hello.csv"
}

world = {
    exec "cp $input.xml $output.tsv"
}

run {
    hello + there + cleanup_stuff + world
}

