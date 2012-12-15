source ../testsupport.sh

run *.bed *.peak

exists s1_ccat_sort.ccat.intersect.peak s2_ccat_sort.ccat.intersect.peak

true
