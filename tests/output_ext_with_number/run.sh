source ../testsupport.sh

run test.txt

exists test.hello.csv
exists test.hello.world.csv

true
