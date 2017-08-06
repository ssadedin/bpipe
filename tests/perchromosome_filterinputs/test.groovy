hello = {
   exec """
     echo "Using bam file $input.bam"
   """
   exec " cat $input.bam > $output.vcf"
}

world = {
       exec """
         echo "world using bam file $input.bam in branch $branch.name"
       """
       exec "cat $input.bam > $output.txt"
}


run {
    chr(1..2) * [ hello + world ]
}
