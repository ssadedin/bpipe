hello = {
    exec "cp -v $input.xml $output.txt"
}

world = {
    exec "cp -v $input.txt $output.csv"
}

run {
    hello + world
}
