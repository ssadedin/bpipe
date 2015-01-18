source ../testsupport.sh

run test.txt

[ -e test.hello.csv ] && err "Did not cleanup file test.hello.csv"

bpipe retry test > test.out

grep -q 'Would execute' test.out && err "Should not re-execute anything on second run"

true
