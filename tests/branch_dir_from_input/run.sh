source ../testsupport.sh

run  test.txt

[ -e foo/test.mybranch.hello.world.xml ] || err "Expected output file in expected directory: foo/test.mybranch.hello.world.xml"

[ -e foo/test.mybranch.hello.csv ] || err "Expected output file in expected directory: foo/test.mybranch.hello.csv"

