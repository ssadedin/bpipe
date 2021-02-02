hello = {
    produce('hello.txt') {
        exec """
            touch $output.txt

            sleep 20
        """
    }
}

run {
    hello
}

