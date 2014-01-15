hello = {
    transform("txt") to("foo.csv","foo.xml") {
        exec """
            cp $input.txt $output.csv

            cp $input.txt $output.xml
        """
    }
}

run {
    hello
}
