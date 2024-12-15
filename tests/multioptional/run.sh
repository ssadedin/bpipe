source ../testsupport.sh

run test.tsv

exists test.hello.tsv || "Did not find expected output file"
