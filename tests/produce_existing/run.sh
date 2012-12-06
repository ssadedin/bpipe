source ../testsupport.sh

run test.txt

exists test.txt.hello test.txt.hello.there test.txt.hello.world

# The stage "there" shouldn't produce this output meta data because 
# meta data for the same output was already produced by the previous stage
notexists .bpipe/outputs/there.test.txt.hello.properties

true

