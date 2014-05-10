source ../testsupport.sh

run test.txt

exists foo.csv foo.world.xml

bpipe cleanup -y  >> test.out

grep -q 'No existing files were found' test.out || err "Failed to observe message saying no files to clean up"

# Should not have been cleaned up, even though intermediate because
# we "preserved" it
exists foo.csv foo.world.xml

true

