hello = {
    produce("foo.bar.txt") {
        exec """
            cat $inputs.bar.txt > $output.txt
        """
    }
}

run { 
    hello 
}
