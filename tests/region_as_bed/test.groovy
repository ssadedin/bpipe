hello = {
    exec """
        echo "I am running on region $region"

        echo "My regions file is $region.bed"
    """
}

run { hello }
