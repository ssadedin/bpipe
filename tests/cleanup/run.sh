
. ../testsupport.sh  
. ./cleanup.sh

run test.txt

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"

[ `grep -c "execute" test.out ` == 4 ] || err "All stages did not execute on first run"

# Running a second time should skip the stages

run test.txt

grep -q "execute" test.out && err "commands executed incorrectly on second run instead of skipped"

# now cleanup

bpipe cleanup -y > test.out

grep -q 'test.html' test.out || err "Failed to find test.html as an output to be cleaned up"
grep -q 'test.csv' test.out || err "Failed to find test.html as an output to be cleaned up"

[ -e test.html ] && err "test.html not cleaned up"
[ -e test.csv ] && err "test.csv not cleaned up"

# now run again
run test.txt

# even though we removed the files, we should still skip creating outputs
grep -q execute test.out && err "Failed to skip all steps after cleanup"

# now touch test.csv - we should NOT recreate test.html, but the later stages SHOULD get created
touch test.csv
run test.txt

grep -q 'execute2' test.out && err 'Failed to skip creating touched file test.csv even though not necessary'
grep -q 'execute1' test.out && err 'Failed to skip creating file test.html even though not necessary'
grep -q 'execute3' test.out || err 'Incorrectly skipped creating file test.xml, even though an input was updated'
grep -q 'execute4' test.out && err 'Failed to skip creating file test.xls even though the input that was updated is not one it depends on'
grep -q 'execute5' test.out && err 'Failed to skip creating file test.xls.me even though it uses no inputs and already existed'

# finally do a token test to make sure 'bpipe query' works
bpipe query > query.test.out || err "Failed to execute bpipe query"

grep -q test.html query.test.out || err "Failed to find test.html in query output"

