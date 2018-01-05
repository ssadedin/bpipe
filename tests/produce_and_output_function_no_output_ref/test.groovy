
/*
 * Here there are two declared outputs by different means:
 *
 *  - one declared by the produce function
 *  - one declared by use of the output function
 *
 * The test is whether the pipeline still create the correct 
 * dependencies for both outputs even though they are declared 
 * different ways
 */

hello = {
    produce("hello.txt") {
        exec """
            echo "Running"

            touch ${output('world.txt')}

            touch hello.txt
        """
    }
}

run { hello }
