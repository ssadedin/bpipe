
samples = channel(sample1: 'sample1.txt',sample2 : 'sample2.txt').named('sample')


hello = {

    exec """

        echo "Processing hello for $sample using $input.txt"

        cp -v $input.txt $output.txt
    """
}

world = {

    exec """

        echo "Processing world for $sample using $input.txt"

        cp -v $input.txt $output.xml
    """
}


run {
    samples * [ hello ] + samples * [ world ]
}


