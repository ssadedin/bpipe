
about title: "My awesome pipeline",
      author: "Simon Sadedin"

foo = {
  doc title: "Foo is a test pipeline",
      desc: """
               This pipeline is only a test.  It doesn't do anything useful,
               but it does help to test Bpipe and ensure the documentation functions
               are working OK.
            """,
      constraints: "Please make sure Bpipe is correctly installed before running the documentation test",
      inputs: ["A bam file containing reads", "CSV file containing statistics"],
      outputs: "Many things"

    exec "/bin/ls -lhtc"
}

GATK_HOME='C:/cygwin/home/ssadedin/gatk/workspace/gatks/GenomeAnalysisTK-1.5-32-g2761da9'

hello = {
  msg "hello"
  exec "sleep 1"
}

bar = {
  doc "A trivial test stage that only sleeps for 1 second and outputs a message"
  msg "world"
  exec "sleep 1"
}

fail = {
  doc title: "Fails Every Time"


  exec """
    echo "I'm a big failure"

    false
  """
}

gatk = {
  exec "java -jar $GATK_HOME/GenomeAnalysisTK.jar -T Help"
}

run {  
   foo +hello + bar + fail
}
