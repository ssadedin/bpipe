hello = {
    exec """
        cp -v $input.txt $output.vcf
    """
}

there = {
    exec """
        cp $input.txt $output.bam
    """
}

world = {

    def correctBam = 'test.bar.there.bam'

    from(correctBam) produce('bar.xml') {
        exec """
            echo "branch $branch.name: create $output.xml from $input.hello.vcf + $input.there.bam correct=$correctBam => $output.xml"

            cat $input.hello.vcf $input.there.bam > $output.xml
        """
    }
}

run {

    [foo : 'test.foo.txt', bar: 'test.bar.txt'] * [
        hello + there
    ]  + world
}
