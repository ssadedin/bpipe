HG19=System.getenv("HG19")?:"/shared/hg19"
HGFA=System.getenv("HGFA")?:"$HG19/gatk.ucsc.hg19.fasta"
GATK=System.getenv("GATK")?:"/mnt/Bioinfo_Slow/simons/GenomeAnalysisTK-1.2-21-g6804ab6"



foo = {
    exec "echo hello > hello.txt"
}

baz = {
    exec "echo hello there > test.foo"
}

bar = {
    transform("bar.txt") {
        msg "echo $input ==> $output"
        exec "echo $input.txt > $output"
    }
}



Bpipe.run {
    "s_%_*.txt" * [ foo + baz + bar ]
}
