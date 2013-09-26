hello = {
    output.dir = "output_dir"
    produce("*.foo") {
        exec "touch output_dir/test1.foo output_dir/test2.foo"
    }
}

@transform("txt")
world = {
    exec "cat $inputs.foo > $output.txt"
}

run { hello + world }
