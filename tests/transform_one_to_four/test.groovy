hello = {
    transform('tab') to('one.bed','two.bed','three.bed','four.bed') {
        exec """
            cp -v $input.tab $output1.bed 

            cp -v $input.tab $output2.bed

            cp -v $input.tab $output3.bed

            cp -v $input.tab $output4.bed 
        """
    }
}

run { 
    hello
}
