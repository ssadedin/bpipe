source ../testsupport.sh

run

grep -q '3:0-1980[0-9]*' test.out || err "Region has unexpected value or no value for size of chromosome 3" 

grep -q '2:0-24319[0-9]*' test.out || err "Region has unexpected value or no value for size of chromosome 2"

