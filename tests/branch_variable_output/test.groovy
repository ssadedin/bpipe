stage_one = {
     exec "touch $output.vcf"    
     branch.stage_one_output = output.vcf
}

stage_23 = {
  from(stage_one_output) {
    exec "cp $input.vcf $output.vcf"
  }
}

run { stage_one + stage_23 }

