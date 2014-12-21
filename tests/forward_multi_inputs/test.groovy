sample_dir_prepare = {
	forward inputs
}

check_inputs = {

  println "Inputs are ${inputs.size()} $inputs"
}

run { sample_dir_prepare + check_inputs }
