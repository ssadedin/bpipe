hello = {
    transform("txt") to("tsv") {
        exec """
            cp $input.txt $output.tsv
        """
    }
}

run {
    hello
}
