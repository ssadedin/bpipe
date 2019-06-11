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

    println "The correct BAM is $correctBam"

    from(correctBam) {
        exec """
            cp -v $input.there.bam  $output.xml
        """
    }
}

run {

    [foo : 'test.foo.txt', bar: 'test.bar.txt'] * [
        hello + there
    ]  + world
}
