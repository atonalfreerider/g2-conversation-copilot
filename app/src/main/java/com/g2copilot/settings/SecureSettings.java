package com.g2copilot.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecureSettings {
    private static final String ALIAS = "g2_copilot_provider_keys";
    private final SharedPreferences prefs;
    SecureSettings(Context context) { prefs = context.getSharedPreferences("connections", Context.MODE_PRIVATE); }

    void put(Provider provider, String key, String model, String url) throws Exception {
        byte[] ivAndCiphertext = encrypt(key);
        prefs.edit().putString(provider.name()+"_key", Base64.encodeToString(ivAndCiphertext, Base64.NO_WRAP))
            .putString(provider.name()+"_model", model).putString(provider.name()+"_url", url)
            .putString("active_provider", provider.name()).apply();
    }
    String key(Provider p) throws Exception {
        String value = prefs.getString(p.name()+"_key", "");
        return value.isEmpty() ? "" : decrypt(Base64.decode(value, Base64.NO_WRAP));
    }
    String model(Provider p) { return prefs.getString(p.name()+"_model", p.defaultModel); }
    String url(Provider p) { return prefs.getString(p.name()+"_url", p.defaultUrl); }
    Provider activeProvider() { try { return Provider.valueOf(prefs.getString("active_provider",Provider.OPENAI.name())); } catch(Exception ignored) { return Provider.OPENAI; } }
    void setActiveProvider(Provider provider) { prefs.edit().putString("active_provider",provider.name()).apply(); }

    private SecretKey secretKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return ((KeyStore.SecretKeyEntry) store.getEntry(ALIAS, null)).getSecretKey();
    }
    private byte[] encrypt(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, secretKey());
        byte[] body = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] result = new byte[1 + cipher.getIV().length + body.length]; result[0] = (byte)cipher.getIV().length;
        System.arraycopy(cipher.getIV(), 0, result, 1, cipher.getIV().length);
        System.arraycopy(body, 0, result, 1 + cipher.getIV().length, body.length); return result;
    }
    private String decrypt(byte[] value) throws Exception {
        int ivLength = value[0] & 0xff; byte[] iv = new byte[ivLength]; byte[] body = new byte[value.length-1-ivLength];
        System.arraycopy(value,1,iv,0,ivLength); System.arraycopy(value,1+ivLength,body,0,body.length);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,secretKey(),new GCMParameterSpec(128,iv));
        return new String(cipher.doFinal(body), StandardCharsets.UTF_8);
    }
}
