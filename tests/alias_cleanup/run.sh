. ../testsupport.sh

run test.groovy test.txt

exists test.hello.world.xml

[ `cat test.hello.world.xml` == "cat" ] || err "File test.hello.world.xml does not contain expected content"

true

