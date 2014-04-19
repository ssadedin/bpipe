source ../testsupport.sh

run test.txt

exists test.hello.csv test.hello.world.tsv

bpipe query test.hello.csv | grep -q 'Preserved:.*yes' || err "Failed to find output test.hello.csv as preserved"

true
