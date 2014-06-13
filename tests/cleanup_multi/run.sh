source ../testsupport.sh

run test.fastq

notexists test.csv

grep -q "Cleaned up file test.csv" test.out || err "Failed to find cleanup message for second output"

true
