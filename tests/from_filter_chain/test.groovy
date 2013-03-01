
hello = {
    from("txt") filter("merge") {
        exec "cat $inputs.txt > $output.txt" 
    }
}

world = {
    from("txt") transform("csv") {
        exec "cat $inputs.txt > $output.csv" 
    }
}


run { hello + world }
