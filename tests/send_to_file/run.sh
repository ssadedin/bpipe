source ../testsupport.sh

bpipe run -p planet=Mars test.groovy > test.out 2>&1

exists test.output.txt

grep -q "Hello Mars, from Earth" test.output.txt  || err "Expected message not found in file generated from template"

true

