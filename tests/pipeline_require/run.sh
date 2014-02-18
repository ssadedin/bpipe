source ../testsupport.sh

run 

grep -q "Variable or parameter 'planet' was not specified" test.out || err "Failed to find expected error message when parameter not specified"

bpipe run -p planet=mars test.groovy > test.out

grep -q "hello mars" test.out || err "did not find expected message 'hello mars' in output"

