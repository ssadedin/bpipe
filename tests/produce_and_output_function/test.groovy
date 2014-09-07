hello = {
    produce("hello.txt") {
        exec """
            touch $output.txt

            touch ${output('world.txt')}
        """
    }
}

run { hello }
