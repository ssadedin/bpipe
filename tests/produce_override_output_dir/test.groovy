hello = {
    produce("foo/test.out.txt") {
        exec """
            mkdir -p foo; cp $input.txt $output.txt
        """
    }
}

run {
    hello
}
