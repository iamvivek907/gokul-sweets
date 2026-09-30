package com.gokulsweets.restaurant.payment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** Checkout links contain bearer tokens. Never store/log them as plaintext. */
@Service
public class CheckoutUrlVault {
    private final String configuredKey;
    public CheckoutUrlVault(@Value("${payment.checkout-encryption-key:}") String configuredKey) {this.configuredKey=configuredKey;}
    private SecretKeySpec key() {
        try {byte[] bytes=Base64.getDecoder().decode(configuredKey);if(bytes.length!=32)throw new IllegalArgumentException();return new SecretKeySpec(bytes,"AES");}
        catch(RuntimeException error){throw new IllegalStateException("Configure a base64 32-byte payment checkout encryption key.");}
    }
    public String seal(String url) {
        if(url==null)return null;
        try {var uri=java.net.URI.create(url);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||url.length()>2048)throw new IllegalArgumentException("Invalid checkout URL.");
            byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(),new GCMParameterSpec(128,nonce));cipher.updateAAD("gokul-payment-checkout-v1".getBytes(StandardCharsets.UTF_8));byte[] encrypted=cipher.doFinal(url.getBytes(StandardCharsets.UTF_8));return "v1:"+Base64.getEncoder().encodeToString(ByteBuffer.allocate(12+encrypted.length).put(nonce).put(encrypted).array());
        }catch(Exception error){throw new IllegalStateException("Could not securely persist payment checkout.",error);}
    }
    public String open(String encoded) {
        if(encoded==null)return null;
        try {if(!encoded.startsWith("v1:"))throw new IllegalArgumentException();ByteBuffer data=ByteBuffer.wrap(Base64.getDecoder().decode(encoded.substring(3)));byte[] nonce=new byte[12];data.get(nonce);byte[] encrypted=new byte[data.remaining()];data.get(encrypted);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,nonce));cipher.updateAAD("gokul-payment-checkout-v1".getBytes(StandardCharsets.UTF_8));return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);
        }catch(Exception error){throw new IllegalStateException("Payment checkout could not be recovered. Check provider status before continuing.",error);}
    }
}
