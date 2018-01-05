hello = {
  produce("test.csv", "test*.xml") {
//  produce("test.csv", "test*.xml") {
    exec "cp $input $output.csv"
    exec """

        for i in 1 2 3; 
        do
            echo "executing $input iteration $i";
            cp $input test${i}.xml;
        done
    """
  }
}

world = {
  println "Inputs are $inputs"
}

run { hello + world } 
