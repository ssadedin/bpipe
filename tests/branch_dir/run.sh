source ../testsupport.sh

run 

[ -e foo/hello.mybranch.world.xml ] || err "Expected output file in expected directory: foo/hello.mybranch.world.xml"

[ -e foo/hello.mybranch.csv ] || err "Expected output file in expected directory: foo/hello.mybranch.csv"

