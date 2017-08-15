source ../testsupport.sh

run

grep -q "foo/bar" test.out && err "Found nested output directory when should be a direct child of main dir"

true
