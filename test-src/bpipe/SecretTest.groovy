package bpipe

import static org.junit.Assert.*
import groovy.json.JsonOutput
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Security

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import org.junit.Test

class SecretTest {

    @Test
    public void test() {
//        def secret = new Secret()
//        
//        secret.encode("test me out man")
        
        Security.addProvider(new BouncyCastleProvider());

        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("X25519", "BC")
        keyPairGen.initialize(255, new SecureRandom())
        KeyPair receiverKeyPair = keyPairGen.generateKeyPair()
        
        // Create the HPKE exchange with receiver's public key
        HPKESecretExchange hpke = new HPKESecretExchange(receiverKeyPair.getPublic())
        
        // Encapsulate a secret
        byte[] mySecret = "This is my secret message".getBytes("UTF-8")
        Map<String, String> result = hpke.encapsulate(mySecret)
        
        // Result is ready for JSON serialization
        println "Ephemeral Public Key: ${result.ephemeralPublicKey}"
        println "Encrypted Secret: ${result.encryptedSecret}"
        
        // Convert to JSON (using Groovy's JsonOutput)
        String jsonMessage = JsonOutput.toJson(result)
        println "JSON: ${jsonMessage}"
        
        
        HPKESecretExchange receiver = new HPKESecretExchange(receiverKeyPair.getPrivate())
        
        // Decapsulate the secret
        byte[] decryptedSecret = receiver.decapsulate(result)
        String decryptedMessage = new String(decryptedSecret, "UTF-8")
        
        println "Decrypted message: ${decryptedMessage}"
        assert decryptedMessage == "This is my secret message"
        
        StringWriter sw = new StringWriter()
        PemWriter pemWriter = new PemWriter(sw)
        pemWriter.writeObject(new PemObject("hell no", receiverKeyPair.getPrivate().getEncoded()))
        pemWriter.close()

        println "The public key is: " + sw.toString()
        
    }

}
