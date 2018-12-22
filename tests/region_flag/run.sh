source ../testsupport.sh


bpipe run -L chr1:10-20 test.groovy > test.out 2>&1

grep  -q 'My region will be .*[0-9a-f]\{8\}.bed' test.out || err "Did not see expected region reported in output"

true

