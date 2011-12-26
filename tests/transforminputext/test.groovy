HG19=System.getenv("HG19")?:"/shared/hg19"
HGFA=System.getenv("HGFA")?:"$HG19/gatk.ucsc.hg19.fasta"
GATK=System.getenv("GATK")?:"/mnt/Bioinfo_Slow/simons/GenomeAnalysisTK-1.2-21-g6804ab6"



foo = {
    exec "echo hello > hello.txt"
}

bar = {
    transform("bar.txt") {
        exec "echo $input.txt > $output"
    }
}



Bpipe.run {
    foo + bar
}
