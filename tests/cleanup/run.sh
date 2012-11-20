
. ../testsupport.sh  
. ./cleanup.sh

run test.txt

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"

grep -q Skipping test.out && err "Found unexpected phrase Skipping steps"

# Running a second time should skip the stages

run test.txt

grep -q "Skipping steps" test.out || err "Failed to find expected phrase Skipping steps"

[ `grep -c "Skipping steps" test.out ` == 4 ] || err "Failed to find 4 counts of phrase Skipping steps"


# now cleanup

bpipe cleanup -y > test.out

grep -q 'test.html' test.out || err "Failed to find test.html as an output to be cleaned up"
grep -q 'test.csv' test.out || err "Failed to find test.html as an output to be cleaned up"

[ -e test.html ] && err "test.html not cleaned up"
[ -e test.csv ] && err "test.csv not cleaned up"

# now run again
run test.txt

# even though we removed the files, we should still skip creating outputs
[ `grep -c "Skipping steps" test.out ` == 4 ] || err "Failed to find 4 counts of phrase Skipping steps"

# now touch test.csv - we should NOT recreate test.html, but the later stages SHOULD get created
touch test.csv
run test.txt
[ `grep -c "Skipping " test.out ` == 3 ] || err "Failed to find 3 counts of phrase Skipping steps after touching test.csv"

grep -q 'Skipping steps to create test.csv' test.out || err 'Failed to skip creating touched file test.csv even though not necessary'
grep -q 'Skipping steps to create test.html' test.out || err 'Failed to skip creating touched file test.html even though not necessary'
grep -q 'Skipping .*to create test.xml'  test.out && err 'Incorrectly skipped creating file test.xml, even though an input was updated'
grep -q 'Skipping .* test.xls' test.out || err 'Failed to skip creating file test.xls even though the input that was updated is not one it depends on'

# finally do a token test to make sure 'bpipe query' works
bpipe query > query.test.out || err "Failed to execute bpipe query"

grep -q test.html query.test.out || err "Failed to find test.html in query output"

