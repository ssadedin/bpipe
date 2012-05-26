source ../testsupport.sh

rm -rf doc

run test.txt

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage bar" test.out || err "Failed to find expected stage world"
grep -q "Stage fail" test.out || err "Failed to find expected stage world"

[ -e doc/index.html ] || err "Unable to find generated documentation"

grep -q "This pipeline is only" doc/index.html || err "Unable to find description for foo stage in index.html"
grep -q "Fails Every Time" doc/index.html || err "Unable to find title for fail stage in index.html"

true
