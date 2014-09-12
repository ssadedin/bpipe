hello = {
    produce("hello.txt") {
        exec """
            echo "Running"

            touch ${output('world.txt')}

            touch hello.txt
        """
    }
}

run { hello }
