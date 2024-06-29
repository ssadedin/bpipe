
hello = {

    exec """

        echo "Processing $sample"

        cp -v $input.txt $output.txt
    """
}

samples = channel([
   fred: file('input1.txt').toPath(),
   bob: file('input2.txt').toPath(),
   paul: file('input3.txt').toPath()
]).named('sample')

run {
    samples * [  hello ]
}
