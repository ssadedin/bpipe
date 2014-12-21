hello = {

    produce("hello1.txt") {
        exec """
            cp $input.txt ${output.dir}/hello1.txt
        """
    }
}

run { hello }
