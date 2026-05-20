hello = {
    exec "echo 'hello' > $output.txt"
}

world = {
    exec "cat $input.txt | sed 's/hello/world/' > $output.csv"
}

run {
    hello + world
}
