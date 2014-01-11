// This case was a bug where filter would not work with an output.dir that was set
// outside the body of the filter in certain cases (see below)
hello = {
   exec "cp $input.txt $output.vcf" 
}

there = {
    exec "cp $input.vcf $output.csv"
}

world = {
    filter("vep") {
        output.dir="variants"
        exec "cp $input.vcf $output.vcf"
    }
}

run {
    hello + there + world
}
