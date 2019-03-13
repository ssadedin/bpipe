
there = {

    exec """
        cp -v $input.vcf $output.vcf
    """
}

world = {
    exec """
        cp -v $input.vcf $output.vcf
    """
}

run {
    [ there + []  + world ]
}
