hello = {
  produce("test.csv", "test*.xml") {
    exec "cp $input $output.csv"
    for(def i in 1..3) {
      exec """
        echo "executing $input"

        cp $input ${input.replaceAll('.txt',i+'.xml')}
      """
    }
  }
}

world = {
  println "Inputs are $inputs"
}

run { hello + world } 
