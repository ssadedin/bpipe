hello = {
   exec "cp $input.txt $output.csv" 
}

world = {
   exec "cp $input.csv $output.xml"

   succeed "no need to say hello to mars"
}

mars = {
   exec "cp $input.csv $output.xml"

   fail "Stage mars should not have executed"
   
}

run { hello + [world + mars] }

