hello = {

    output.dir="foo"

    produce("testoutput/test.html","test.csv") {
        exec """
            mkdir -p testoutput

            touch $output.html $output.csv
        """
    }
}

run { hello }
