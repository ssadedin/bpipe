hello = {
    transform(".bam") to(".median.txt") {
        exec """cp $input.bam $output.foo.txt"""
    }
}

run { 
    hello
}
