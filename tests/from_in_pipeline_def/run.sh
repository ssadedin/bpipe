source ../testsupport.sh

run test.txt

grep -q "world: test.hello.csv" test.out || err "expected input test.hello.csv not referenced in world stage"
grep -q "world2: test.there.csv" test.out || err "expected input test.there.csv not referenced in world stage"

grep -q "take_me: test.hello.csv test.there.xml baz" test.out || err "expected inputs test.hello.csv test.there.xml not referenced"

true



