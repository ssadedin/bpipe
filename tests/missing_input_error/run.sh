source ../testsupport.sh

run test.tsv

grep -q 'One or more provided inputs could not be resolved to an existing file' test.out || err 'Did not print expected error message about missing file'

true
