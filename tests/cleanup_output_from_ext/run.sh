source ../testsupport.sh

# Should fail!
run test.txt

# Should clean up this file
notexists test.hello.csv

true
