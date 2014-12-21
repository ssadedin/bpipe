
cutadapt = {
    produce(["cutadapt.1.fq.gz", "cutadapt.2.fq.gz"]){
         exec "touch $output1; touch $output2; " 
    }
}

tophat = {
      if(input1.toString() == input2.toString())
          fail "inputs should not be equal"
}

run { cutadapt + [tophat] }
