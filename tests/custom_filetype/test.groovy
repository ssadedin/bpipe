
// if an input is requested for vcfgz, all of these file types will match
filetype vcfgz : ['vcf','vcf.gz','vcf.bgz']

hello = {
    // whatever extension happened to match the .vcfgz is what gets replaced when creating .vcf.gz as the output extension
    exec """

        echo "The suffix is $input.vcfgz.suffix"

        cp -v $input.vcfgz $output.csv
    """
}

world = {
    exec """
        cp -v $input2.vcfgz $output.csv
    """
}

there = {
    from('test2.vcf.gz') produce('hoohar.csv') {
        exec """
            cp -v $input.vcfgz $output.csv
        """
    }
}

take = {
    transform('vcfgz') to('take.vcf') {
        exec """
            cp -v $input.vcfgz $output.vcf
        """
    }
}

all = {
    transform('*.vcfgz') to('xml') {
        exec """
            set -x

            cat $inputs.vcfgz > $output.xml
        """
    }
}

my = {
    exec """
        cp -v $input.take.vcfgz $output.vcf
    """
}


run {
    hello + world + there + take +  all + my 
}
