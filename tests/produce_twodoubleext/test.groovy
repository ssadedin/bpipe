hello = { 
    produce("foo.bar1.txt", "foo.bar2.txt") {
        exec """
            touch $output.bar2.txt

            touch $output.bar1.txt
        """
    }
}

run { hello }
