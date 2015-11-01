#
# This test is disabled in the normal test run because it is too intensive

source ../testsupport.sh

exit 0


# Create the 200 input files
for i in {1..200}; do touch test.$i.txt; done

bpipe run test.groovy  test.*.txt > test.out


# Does nothing: takes ~ 1 minute 30 seconds (2012 Macbook Pro)
bpipe retry 

