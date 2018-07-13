hello = {
    
    produce(["out.tsv", "out.tsv.gz"]) {
        exec """

            cat $inputs.bam > $output.tsv 

            cat $inputs.bam | gzip -c > $output.tsv.gz
        """
    }
}

run {
    hello
}
