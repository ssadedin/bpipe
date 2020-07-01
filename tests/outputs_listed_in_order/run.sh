source ../testsupport.sh

run test.xml

sleep 3

run anothertest.xml

exists anothertest.hello.world.csv

rm anothertest.hello*

run anothertest.xml

tail -n 1 test.out | grep -v another | grep -q 'test.hello.world.csv' || err "Older output should have been last listed"

tail -n 2 test.out | head -n 1 | grep -q anothertest.hello.world.csv || err "Newer output not listed"

