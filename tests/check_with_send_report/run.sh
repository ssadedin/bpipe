source ../testsupport.sh

run test.txt

exists test.html

grep -q "Oh dear, it failed" test.html || err "Did not find expected message in test.html"

grep -q "Failed" test.html || err "Did not find expected message in test.html"

true
