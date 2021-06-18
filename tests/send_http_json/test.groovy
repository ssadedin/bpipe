import com.sun.net.httpserver.*

// only supports basic web content types
def port = 8680

received = null

jsonParser = new groovy.json.JsonSlurper()

var RETRY : false

def server = HttpServer.create(new InetSocketAddress(port), 0)
    server.createContext("/", { HttpExchange exchange ->    
        try {
            def path = exchange.requestURI.path

            println "GET $path"

            received = exchange.requestBody.text

            println "Received: " + received

            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody << '{"status":"ok"}'
            exchange.responseBody.close()
        } catch(e) {
            e.printStackTrace()
        }
} as HttpHandler)

server.start()

Thread.sleep(1000)

hello = {
    exec """
        echo '{"hi":1, "there":2}' > $output.json
    """
}

there = {
    send json(['hello':1, 'world':2]) to(url:'http://localhost:8680/test')

    Thread.sleep(2000)

    if(RETRY) {
        assert !received
    }
    else
        assert jsonParser.parseText(received) == ["hello":1,"world":2] 
}

world = {
    send json(input.json) to(url:'http://localhost:8680/test')

    Thread.sleep(2000)

    if(RETRY) {
        assert !received
    }
    else
        assert jsonParser.parseText(received) == ["hi":1,"there":2] 

}

stop = {
    server.stop(1)
}

run {
    hello + there + world + stop
}
