source ../testsupport.sh

touch test.txt

run test.txt

exists test.txt.hello

# first run SHOULD execute the check
grep -q "Executing check" test.out || err "Failed to execute check"

run test.txt

# second run should NOT execute the check
grep -q "Executing check" test.out && err "Executed check when not necessary"

# touch input file should cause check to get executed again
touch test.txt
run test.txt
grep -q "Executing check" test.out || err "Failed to execute check"


source ./cleanup.sh
bpipe run test2.groovy > test.out
grep -q "Executing check" test.out || err "Failed to execute check"
grep -q "WARNING: 1 check" test.out || err "Check executed but did not execute otherwise clause when should have failed"

true
