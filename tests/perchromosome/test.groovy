
hello_there = {
    filter("hello") {
	exec """ echo "hello there $chr" > $output """
    }
}

world = {
    transform("csv") {
	exec """ cp $input $output """
    }
}

noinput = {
   msg "hello $chr ==> $output"
}

run {
  chr(1..22,"X","Y","M") * [ hello_there + world ] 
}
