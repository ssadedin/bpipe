hello = {
  exec "cp $input.bam $output.bam"
}

run {
  chr(1..2) * [ hello ]
}
