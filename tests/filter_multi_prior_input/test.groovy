hello = {
    produce("test1.hello.csv", "test1.hello.xml") {
        exec "cp $input.txt test1.hello.csv"
        exec "cp $input.txt test1.hello.xml"
    }
}

world = {
    filter("goo") {
        exec "echo '${inputs.txt.withFlag("--test")}' > $output.txt"
    }
}

run {
    hello + world
}
