hello = {

    uses(db:1) {
        exec """
            echo ">>>>>> Using resource $branch <<<<<<"

            sleep 5

            echo "<<<<<< End using resource $branch >>>>"
        """
    }
}

run {
    ["foo","bar","tree"] * [ hello ]
}
