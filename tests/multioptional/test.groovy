
hello = {
    exec """
        cp -v $input.tsv  $output.tsv
    """
}

world = {

   def testinps = inputs.xml.optional

   if(testinps.size() > 0) {
       assert false : "There are xml inputs $testinps - this is wrong"
   }
   else {
       println "There are no xml inputs: this is correct"
   }

   def withFlagInps = inputs.xml.optional.withFlag("--foo")

   println "With flag: $withFlagInps"

    exec """
        echo "hello $inputs.xml.optional"
    """
}

run { hello + world }
