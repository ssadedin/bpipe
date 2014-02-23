hello = {
    check { 
        exec """
            true
        """
    } otherwise {
        fail "This should not have happened!"
    }
}

world = {
    check { 
        exec """
            grep -q sdfsdffs $input.txt
        """
    } otherwise {
        println "The check failed!"
        // send text {"test email"} to gmail
        //succeed "A really bad thing happened & I am not <b>bold</b> to admit it"
        check.message = "Oh dear, it failed."
    }
}


send_report = {
    send report('checks.html') to file: 'test.html'
}

run { hello + world + send_report }
