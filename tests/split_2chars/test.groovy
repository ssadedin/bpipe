hello = {
	  exec "cat $inputs.txt > $output.txt"
}

run {
  "%_H" * [ hello ]
}
