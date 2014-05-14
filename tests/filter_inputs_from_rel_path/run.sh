source ../testsupport.sh

run ../filter_inputs_from_rel_path/test.xml  ../filter_inputs_from_rel_path/test1.av ../filter_inputs_from_rel_path/test2.av

grep -q "Valid outputs are: test.merge.xml" test.out && err "Found incorrect message about valid outputs"

exists test1.merge.av

