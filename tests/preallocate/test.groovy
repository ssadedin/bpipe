hello = {

    Thread.sleep(2000)

    exec """
        echo "Hello berry world: $POOL_ID"; sleep 3;

        touch $output.xml

    ""","berry"
}

world = {

    Thread.sleep(2000)

    exec """
        echo "Hello juicy world: $POOL_ID"; sleep 3;

        touch $output.csv

    ""","juice"

}

run { [ hello , world ] + hello }
