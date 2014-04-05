hello = {
	exec """
		cp $input.txt $output.csv
	"""
}

there = {
	exec """
		cp $input.csv $output.tsv
	"""
}


world = {
	exec """
		cp $input.csv $output.xml
	"""
}

make_report = {
	send report("test.html") to file: "output.html"
}

run { 
	["foo","bar"] * [ 
	 	 ["frog","cat"] * [hello + world ]  
        ]+ make_report
}
