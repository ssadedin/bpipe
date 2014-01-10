
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
