/* The example from the documentation */

hello = {  
        // Build a list of commands to execute in parallel
        def outputs = (1..3).collect { "out${it}.txt" }

        // Compute the commands we are going to execute
	int n = 0
        def commands =["mars","jupiter","earth"].collect{"echo $it > ${outputs[n++]}"} 

        // Tell Bpipe to produce the outputs from the commands
	produce(outputs) {
	    multiExec commands
	}
}

run { hello }
