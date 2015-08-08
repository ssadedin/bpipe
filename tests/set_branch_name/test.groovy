
init_hello = {
    branch.name = "foo"
}

hello = {
    exec "cp $input.txt $output.txt"
}

run {
    init_hello + chr(1..2, filterInputs:false) * [ hello ]
}
