. ../testsupport.sh

run test.groovy test.txt

exists crazy.world.xml

[ `cat crazy.world.xml` == "cat" ] || err "File crazy.world.xml does not contain expected content"

true

