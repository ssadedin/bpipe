hello = {
    exec """
        cp -v $input.txt $output.csv
    """
}

PRE_FAIL=true

POST_FAIL=false

WITH_OTHERWISE=false

world = {
    def c = check "the world is failing",  {
        exec """
            ${!PRE_FAIL}

            cp -v $input.csv $output.xml

            ${!POST_FAIL}
        """
    }

    if(WITH_OTHERWISE) {
        c.otherwise {
            println "\nOtherwise I am fine\n"
        }
    }
}

run {
    hello + world
}
