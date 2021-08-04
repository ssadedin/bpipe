hello = {
    transform('*.g.vcf.gz') to('.vcf.gz','.trio.gvcf.gz') {
        exec """
            cat $inputs.gz > $output.vcf.gz

            cat $inputs.gz >   $output.trio.gvcf.gz
        """
    }
}

run { hello }
