hello = {
    exec """
        cp -v $input.txt $output.csv
    """
}

there = {
    exec """
        cp -v $input.txt $output.csv

        cp -v $input.txt $output.xml

    """
}

world = {
    exec """
        echo "world: $inputs.csv $output.xml"
        echo "world2: $inputs.there.csv $output2.xml"

        cp -v $inputs.csv $output.xml

        cp -v $inputs.there.csv $output2.xml
    """
}

take_me = {

    exec """
        echo "take_me: $input.csv $input.xml"

        cat $input.csv $input.xml > $output.tsv
    """
}

run {
    hello + there + world.from(hello) + take_me.from(hello,there)
}


