hello = {

    Thread.sleep(2000)

    exec """
        echo "Hello berry world: $POOL_ID"; sleep 3;
    ""","berry"
}

world = {

    Thread.sleep(2000)

    exec """
        echo "Hello juicy world: $POOL_ID"; sleep 3;
    ""","juice"

}

run { hello + world }
