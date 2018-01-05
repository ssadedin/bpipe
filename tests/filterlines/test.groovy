/*
 * Test of filterLines - and undocumented feature at this point, but
 * useful to test
 */
hello = {
    filter("hello") {
	    filterLines { true } 
    }
}

run {
	hello 
}
