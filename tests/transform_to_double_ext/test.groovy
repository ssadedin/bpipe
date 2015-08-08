hello = {
    transform(".bam") to(".median.txt") {
        exec """cp $input.bam $output.median.txt"""
    }
}

run { 
    hello
}
