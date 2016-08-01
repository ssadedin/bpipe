source ../testsupport.sh

bpipe execute -r --source load_me.groovy 'hello' > test.out

grep -q "Hello World" test.out || err "Failed to find Hello World message in output"

true


