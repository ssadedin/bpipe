source ../testsupport.sh

run test*.txt

exists test1.tsv
notexists test2.tsv

grep -q 'ERROR:' test.out && err "Error reported when doing one-one transform on multiple matching inputs"

true

