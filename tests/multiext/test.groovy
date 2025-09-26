

hello = {
    produce('foo.gvcf.gz', 'foo.vcf.gz') {
        exec """
            touch $output.gvcf.gz $output.vcf.gz
        """
    }
}

world = {
    exec """
        cp -v $input.vcf.gz $output.txt
    """
}

run {
    hello + world
}

