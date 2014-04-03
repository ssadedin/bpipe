source ../testsupport.sh

run test.txt

exists hello.txt

exists hello.there.txt fubar.csv

grep -q there fubar.csv && err "Found 'there' in output file indicating wrong input used in stage world"

true
