hello = {
    exec "cp -v $input.bam $output.bam"
    forward input.bam
}

there = {
    exec "cp -v $input.bam $output.bam"
}

run {
    hello + there
}
