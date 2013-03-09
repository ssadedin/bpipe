init = {
  // start by cleaning up all the files
  ["dummy1.log.txt","dummy2.log.txt","dummy3.log.txt"].collect { new File(it) }.each { if(it.exists()) it.delete() } 
  forward null
}

hello = {
  exec """for i in 1 2 3;  do echo "Hello World $i"; sleep 2; done """

  if(!new File("dummy1.log.txt").exists())
    throw new Exception("Failed to create expected log dummy1.log.txt")
}

world = {
  exec """echo "run with dummy2" """, "other_config"

  if(!new File("dummy2.log.txt").exists())
    throw new Exception("Failed to create expected log dummy2.log.txt")
}

there = {
  exec "ls | wc"

  if(!new File("dummy3.log.txt").exists())
    throw new Exception("Failed to create expected log dummy3.log.txt")
}

body = {
    config("other_config") {

      def dummy2 = new File("dummy2.log.txt")

      dummy2.delete()

      exec """echo "run with dummy2" """

      if(!dummy2.exists())
        throw new Exception("Failed to create expected log dummy2.log.txt")
    }
}

// Make sure we fail if the executor returns fail exit code from status
fail = {
  exec "false"
}

run {
  init + hello + there + world + body + fail
}
