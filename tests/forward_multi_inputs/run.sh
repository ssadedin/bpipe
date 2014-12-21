source ../testsupport.sh

run test_1.fq.gz test_2.fq.gz 


# Ensure that these two inputs get correctly forwarded
grep -q "Inputs are 2 test_1.fq.gz test_2.fq.gz" test.out || err "Failed to find 2 inputs to second stage as expected"

true
