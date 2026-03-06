/*
 * Copyright (c) 2025 MCRI, authors
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package bpipe

import java.nio.charset.StandardCharsets
import java.security.*
import org.bouncycastle.crypto.hpke.HPKE
import org.bouncycastle.jce.provider.BouncyCastleProvider

class Secret {
    
    void encode2(String secret) {
        Security.addProvider(new BouncyCastleProvider());
        
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519", "BC");
        KeyPair receiver = kpg.generateKeyPair();
        byte[] receiverPub = receiver.getPublic().getEncoded();
        byte[] receiverPriv = receiver.getPrivate().getEncoded();
        
        
        byte[] plaintext = secret.getBytes(StandardCharsets.UTF_8);
        
       
        byte mode = 0;
        short kem  = HPKE.kem_X25519_SHA256
        short kdf  = HPKE.kdf_HKDF_SHA256;
        short aead = HPKE.aead_CHACHA20_POLY1305
        
        // Construct HPKE with explicit parameters
        HPKE hpke = new HPKE(mode, kem, kdf, aead);
         
//        def senderCtx = hpke.setupBaseS();
    }
    
    void encode(String secret) {
        byte mode = 0;
        short kem  = HPKE.kem_X25519_SHA256
        short kdf  = HPKE.kdf_HKDF_SHA256;
        short aead = HPKE.aead_CHACHA20_POLY1305
        
        // Construct HPKE with explicit parameters
        HPKE hpke = new HPKE(mode, kem, kdf, aead);
        
        hpke.setup
        
//        HPKE.SenderContext senderCtx = hpke.setupBaseS(
//            new HPKE.PublicKey(receiverPub),
//            null // info
//        );
//        
//        byte[] ciphertext = senderCtx.seal(null, plaintext);
//        byte[] enc = senderCtx.getEncapsulation();
//        
//        HPKE.ReceiverContext receiverCtx = hpke.setupBaseR(
//            new HPKE.PrivateKey(receiverPriv),
//            enc,
//            null
//        );
//        byte[] decrypted = receiverCtx.open(null, ciphertext);
        
    }

}
