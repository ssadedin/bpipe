
hello = {
    preserve("*.bai") {
        transform("bam") to("bam.bai") {               
            exec "cp -v $input.bam ${input.bam.replaceAll('bam','bam.bai')}"
        }
        forward input
    }
}

run {
    hello
}
