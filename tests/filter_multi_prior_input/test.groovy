hello = {
    exec "cp $input.txt $output.csv"
    exec "cp $input.txt $output.xml"
}

world = {
    filter("goo") {
        exec "cat $inputs.txt > $output.txt"
    }
}

run {
    hello + world
}
