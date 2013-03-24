source ../testsupport.sh

run test.txt

exists test.hello.world.xml
exists test.hello.world_with_filter.xml
exists test.hello.world_with_mismatch_filter.xml
exists test.hello.csv

bpipe cleanup -y >> test.out

notexists test.hello.world.xml
notexists test.hello.csv
notexists test.hello.world_with_filter.xml

# Because the filter mismatches, this output should have survived
exists test.hello.world_with_mismatch_filter.xml

true
