
align = {
  exec "cp $input.fastq $output.bam"
}

@accompanies("bam")
index = {
  transform("bai") {
    exec "cp $input.bam $output.bai"
  }
  forward input
}

filter_bam = {
  exec "cp $input.bam $output.bam"
}

run { align + index + filter_bam }
