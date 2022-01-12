hello = {
    exec """
        cp -v $input.txt $output.tsv
    """
}

there = {
    exec """
        cp -v $input.tsv $output.tsv
    """
}

world = {
    exec """
        cp -v $input.tsv $output.xml
    """
}

run {
    ['a','b'] * [ hello + there ] + world.from('hello.tsv', branch: 'b')
}

