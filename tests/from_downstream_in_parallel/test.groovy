// Check that inputs can be resolved by a nested pipeline
// up the chain from a nested, parent pipeline
hello = {
    transform("csv") {
        exec "cp $input $output"
    }
}

there = {
    transform("xml") {
        exec "cp $input $output"
    }
}

world = {
    from("csv") {
        exec "cp $input $output"
    }
}

mars = {
    exec "cp $input.csv $output"
}


run {
    "%" * [
        hello + there + [ world , mars ]
    ]
}
