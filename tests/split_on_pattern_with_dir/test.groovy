/*
   fastqCaseFormat=cases/%_R*.fastq.gz and the files from that run are cases/EKL14-10_R1.fastq.gz, cases/EKL14-10_R2.fastq.gz, controls/SRX372044_R1.fastq.gz, controls/SRX372044_R2.fastq.gz
   */

fastq_dedupe = {
    println "The inputs are $inputs"
}

fastqCaseFormat="cases/%_R*.fastq.gz"
fastqControlFormat="controls/%_R*.fastq.gz"

run { 
    fastqCaseFormat * [ fastq_dedupe ]
}
