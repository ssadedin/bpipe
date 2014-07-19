
 
testforward = {
 
        exec "cp ${input} ${output}"
        forward output,input
}
 
echoInput = {
        inputs.each() {
                println it
        }

        forward input
}

echoAgain = {
  exec "cp $input $output"
   
  forward([output,input])
}

end = {
  exec """
    echo "This is the end: $input"
  """
}
 
run {
        testforward + echoInput + echoAgain + end
}
 

