source ../testsupport.sh

bpipe test test.groovy > test.out

grep -q 'executor:sge' test.out || err "Failed to find correct executor in output"

rm -rf .bpipe

bpipe run test.groovy > test.out 2>&1 # will fail

[ -e .bpipe/commandtmp/1/cmd.sh ] || err "Did not find command template written to expected folder"

true

