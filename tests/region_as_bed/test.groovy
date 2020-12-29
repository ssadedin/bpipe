hello = {
    exec """
        echo "I am running on region $region"

        echo "My regions file is $region.bed"

        if [ ! -e $region.bed ]; then exit 1; fi
    """
}

run { hello }
