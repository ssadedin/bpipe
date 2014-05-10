hello = {
    def outputDir = file(".")
    preserve(outputDir.absolutePath+"/*.csv") {
        produce("foo.csv") {
            exec """
                cp $input.txt $output.csv
            """
        }
    }
}

world = {
    exec """
        cp $input.csv $output.xml
    """
}

run { hello + world }


