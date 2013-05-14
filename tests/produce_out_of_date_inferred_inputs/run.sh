source ../testsupport.sh

run test.txt test1.xml test2.xml 

# Both stages should have executed and printed "executing"
grep -q "executing1" test.out || err "Failed to find command 1 executing"
grep -q "executing2" test.out || err "Failed to find command 2 executing"


# Necessary on Macs and others with only 1 second resolution in file timestamps
sleep 1;

# update the input
touch test1.xml

# probably not necessary, but let's make our timestamps really clear
sleep 1; 

run test.txt test1.xml test2.xml

# First stage should NOT execute (it does not depend on test1.xml)
grep -q "executing1" test.out && err "Failed to find command 1 executing"

# Second stage SHOULD execute (it DOES depend on test1.xml)
grep -q "executing2" test.out || err "Failed to find command 2 executing"

true

