// The index files are created as a side effect: they are NOT declared outputs
make_bam = {
    produce("test.bam", "test.dat") {
        exec """
            echo REAL bam > test.bam;
            echo REAL bai > test.bam.bai;
            echo REAL dat > test.dat;
            echo REAL didx > test.didx
        """
    }
}

use_index = {
    produce("result.txt") {
        exec "cat $input.bam ${input.bam}.bai test.didx > $output"
    }
}

run { make_bam + use_index }
