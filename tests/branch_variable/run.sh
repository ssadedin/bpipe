source ../testsupport.sh

run  test.txt test_*.xsl 

exists test.hello.world.there.xml test_1.greetings.bed test_2.greetings.bed

[ `grep -c bar test.hello.world.there.xml` == 2 ] || err "Failed to find 2 lines with bar in test.hello.world.there.xml"

grep -q bar test_1.greetings.bed || err "Failed to find bar in output from child pipeline from pattern split"
grep -q bar test_2.greetings.bed || err "Failed to find bar in output from child pipeline from pattern split"

true

