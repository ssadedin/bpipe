

hello = {
    exec """
        cp -v $input.bam $output.bam
    """
}


earth = {
    exec """
        cp -v $input.bam $output.bam
    """
}

mars = {
    println "I doooo NOOOTHING"
}


there = {
    println "Input to there is $input.bam"
}

run {
    hello + [mars, earth] + there
}


