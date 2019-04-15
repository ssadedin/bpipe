source ../testsupport.sh

# Should fail check
bpipe run -p a=1 test.groovy  > test.fail.out 2>&1

grep -q 'Hmmm 1 <= 2' test.fail.out || err "Failed to see print from otherwise clause for failing check"

rm hello.txt

# Should pass check
bpipe run -p a=10 test.groovy  > test.pass.out 2>&1

grep -q 'Hmmm 10 <= 2' test.pass.out && err "Found print from otherwise clause even though check passed"

true



