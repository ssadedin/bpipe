hello = {
    produce("hello.txt") {
        exec "cp $input.txt $output.txt"
    }
}

there = {
    exec "cp $input.txt $output.txt; echo there >>  $output.txt"
}

world = {
    transform("hello.txt") to("fubar.csv") {
        exec "cp $input.txt $output.csv"
    }
}

run { hello + there + world }
