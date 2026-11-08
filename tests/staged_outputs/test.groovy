hello = {
    exec "echo REAL hello threads=$threads > $output.txt"
}

world = {
    exec "cat $input.txt > $output.csv && echo REAL world > $output.tsv"
}

run { hello + world }
