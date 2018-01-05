fastqc = {
    output.dir="ABC"
    produce("*.fastqc.zip") {
        exec "echo exec; touch $output.dir/test.fastqc.zip"
    }
}
run {
    fastqc
}
