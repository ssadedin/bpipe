
init = {
    exec """
        cp $input.xml $output.bed
         """
}

extract_amplicon_fasta = {

    doc "Extracts FASTA for amplicons in the given BED file"

    requires AMPLICON_BED : "File of amplicons provided by Agilent"

    from(AMPLICON_BED) {
        transform(".bed") to(".fasta") { 
            exec """
                cp $input.bed $output
            """
        }
    }
}

run {
    init + extract_amplicon_fasta
}
