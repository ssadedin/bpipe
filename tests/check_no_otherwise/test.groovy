hello = {
    exec """
        echo hello > $output.txt
    """

    check("eq1") {
        exec """
            [ `echo $output.txt | wc -l` -eq 1 ]
        """
    }

    check("eq2") {
        exec """
            [ `echo $output.txt | wc -l`  -eq 2 ]
        """
    }
}

run { hello }
