
hello = {
    exec """
        cp $input.txt $output.csv
    """
}

run {
    hello + ['aaa','bbb','ccc','ddd'] * [ hello.using(foo:'bar') ]
}
