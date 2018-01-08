source ../testsupport.sh

# First run straight through
run test.groovy

exists foo.txt bar.txt fubar.txt

# Now cleanup just bar.txt
bpipe cleanup -y bar.txt > test.out

notexists bar.txt

# Now check that rerunning does NOT attempt to recreate the file
bpipe retry test > test.out

grep -q 'Would execute' test.out && err "Should not have tried to recreate cleaned up file bar.txt"

true
