
@produce("foo.txt")
hello = {
    exec "echo hello; touch $output.txt"
}

@produce("bar.txt")
world = {
    exec "echo world; cp $input.txt $output.txt"
}

@produce("fubar.txt")
there = {
    exec "echo there; cp $input.txt $output.txt"
}



run { hello + world + there }


