source ../testsupport.sh

run test.txt

exists output.tsv

bpipe run test.groovy test.txt > test.out2

grep -q "Skipping command" test.out2 || err "Failed to skip command on second run"

true
