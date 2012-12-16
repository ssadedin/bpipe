source ../testsupport.sh

run *.bed *.peak

exists s2_macs_sort.macs.intersect.bed s1_macs_sort.macs.intersect.bed

true
