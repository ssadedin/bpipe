hello = {
    exec "cp $input.txt $output.xml"
}

world = {

    def wrong_input = input.xml

    from('test.txt') produce('something.txt') {
        exec """
            cp -v  $input $output
        """
    }
}

run {
    hello + world
}
