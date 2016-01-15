/**
  testing the probe() feature.

  This feature lets a user probe whether an input is available or not without 
  triggering a failure

**/

hello = {

    println "1 The inputs ending with foo.txt are ${input.probe('.foo.txt')}"
}

/* the same as 'hello' but with inputs instead of input */
there = {
    println "2 The inputs ending with foo.txt are ${inputs.probe('.foo.txt')}"
}

world = {
    println "3 The inputs ending with bar.txt are ${input.probe('.bar.txt')}"

    if(!input.probe('.bar.txt')) {
        println "No bar.txt input, excellent"
    }
    else 
    transform('bar.txt') to('.xml') {
        exec """
            cp $input.csv $output.xml
        """
    }
}

/* the same as 'hello' but with inputs instead of input */
and_mars = {

    def inps = inputs.probe(~'\\.f.o\\.txt')
    println "The inputs ending with foo.txt are ${inps}"
    if(!inps)
        println "WRONG, didn't find the regex pattern"
    else
        println "CORRECT, found regex pattern"

}



run { hello + there + world  + and_mars}
