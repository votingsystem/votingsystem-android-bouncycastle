package org.bouncycastle2.cert.crmf.jcajce;

import org.bouncycastle2.asn1.*;
import org.bouncycastle2.asn1.iana.IANAObjectIdentifiers;
import org.bouncycastle2.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle2.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle2.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle2.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle2.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle2.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle2.cert.crmf.CRMFException;
import org.bouncycastle2.cms.CMSAlgorithm;
import org.bouncycastle2.cms.CMSEnvelopedDataGenerator;
import org.bouncycastle2.jcajce.JcaJceHelper;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.RC2ParameterSpec;
import java.io.IOException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.Map;

class CRMFHelper
{
    protected static final Map BASE_CIPHER_NAMES = new HashMap();
    protected static final Map CIPHER_ALG_NAMES = new HashMap();
    protected static final Map DIGEST_ALG_NAMES = new HashMap();
    protected static final Map KEY_ALG_NAMES = new HashMap();
    protected static final Map MAC_ALG_NAMES = new HashMap();

    static
    {
        BASE_CIPHER_NAMES.put(PKCSObjectIdentifiers.des_EDE3_CBC,  "DESEDE");
        BASE_CIPHER_NAMES.put(NISTObjectIdentifiers.id_aes128_CBC,  "AES");
        BASE_CIPHER_NAMES.put(NISTObjectIdentifiers.id_aes192_CBC,  "AES");
        BASE_CIPHER_NAMES.put(NISTObjectIdentifiers.id_aes256_CBC,  "AES");
        
        CIPHER_ALG_NAMES.put(CMSAlgorithm.DES_EDE3_CBC,  "DESEDE/CBC/PKCS5Padding");
        CIPHER_ALG_NAMES.put(CMSAlgorithm.AES128_CBC,  "AES/CBC/PKCS5Padding");
        CIPHER_ALG_NAMES.put(CMSAlgorithm.AES192_CBC,  "AES/CBC/PKCS5Padding");
        CIPHER_ALG_NAMES.put(CMSAlgorithm.AES256_CBC,  "AES/CBC/PKCS5Padding");
        CIPHER_ALG_NAMES.put(new ASN1ObjectIdentifier(PKCSObjectIdentifiers.rsaEncryption.getId()), "RSA/ECB/PKCS1Padding");
        
        DIGEST_ALG_NAMES.put(OIWObjectIdentifiers.idSHA1, "SHA1");
        DIGEST_ALG_NAMES.put(NISTObjectIdentifiers.id_sha224, "SHA224");
        DIGEST_ALG_NAMES.put(NISTObjectIdentifiers.id_sha256, "SHA256");
        DIGEST_ALG_NAMES.put(NISTObjectIdentifiers.id_sha384, "SHA384");
        DIGEST_ALG_NAMES.put(NISTObjectIdentifiers.id_sha512, "SHA512");

        MAC_ALG_NAMES.put(IANAObjectIdentifiers.hmacSHA1, "HMACSHA1");
        MAC_ALG_NAMES.put(PKCSObjectIdentifiers.id_hmacWithSHA1, "HMACSHA1");
        MAC_ALG_NAMES.put(PKCSObjectIdentifiers.id_hmacWithSHA224, "HMACSHA224");
        MAC_ALG_NAMES.put(PKCSObjectIdentifiers.id_hmacWithSHA256, "HMACSHA256");
        MAC_ALG_NAMES.put(PKCSObjectIdentifiers.id_hmacWithSHA384, "HMACSHA384");
        MAC_ALG_NAMES.put(PKCSObjectIdentifiers.id_hmacWithSHA512, "HMACSHA512");

        KEY_ALG_NAMES.put(PKCSObjectIdentifiers.rsaEncryption, "RSA");
        KEY_ALG_NAMES.put(X9ObjectIdentifiers.id_dsa, "DSA");
    }

    private JcaJceHelper helper;

    CRMFHelper(JcaJceHelper helper)
    {
        this.helper = helper;
    }

    PublicKey toPublicKey(SubjectPublicKeyInfo subjectPublicKeyInfo)
        throws CRMFException
    {
        X509EncodedKeySpec xspec = new X509EncodedKeySpec(new DERBitString(subjectPublicKeyInfo).getBytes());
        AlgorithmIdentifier keyAlg = subjectPublicKeyInfo.getAlgorithmId();

        try
        {
            return createKeyFactory(keyAlg.getAlgorithm()).generatePublic(xspec);
        }
        catch (InvalidKeySpecException e)
        {
            throw new CRMFException("invalid key: " + e.getMessage(), e);
        }
    }

    Cipher createCipher(ASN1ObjectIdentifier algorithm)
        throws CRMFException
    {
        try
        {
            String cipherName = (String)CIPHER_ALG_NAMES.get(algorithm);

            if (cipherName != null)
            {
                try
                {
                    // this is reversed as the Sun policy files now allow unlimited strength RSA
                    return helper.createCipher(cipherName);
                }
                catch (NoSuchAlgorithmException e)
                {
                    // Ignore
                }
            }
            return helper.createCipher(algorithm.getId());
        }
        catch (GeneralSecurityException e)
        {
            throw new CRMFException("cannot create cipher: " + e.getMessage(), e);
        }
    }
    
    public KeyGenerator createKeyGenerator(ASN1ObjectIdentifier algorithm)
        throws CRMFException
    {
        try
        {
            String cipherName = (String)BASE_CIPHER_NAMES.get(algorithm);

            if (cipherName != null)
            {
                try
                {
                    // this is reversed as the Sun policy files now allow unlimited strength RSA
                    return helper.createKeyGenerator(cipherName);
                }
                catch (NoSuchAlgorithmException e)
                {
                    // Ignore
                }
            }
            return helper.createKeyGenerator(algorithm.getId());
        }
        catch (GeneralSecurityException e)
        {
            throw new CRMFException("cannot create key generator: " + e.getMessage(), e);
        }
    }
    
    Cipher createContentCipher(final Key sKey, final AlgorithmIdentifier encryptionAlgID)
        throws CRMFException
    {
        return (Cipher)execute(new JCECallback()
        {
            public Object doInJCE()
                throws CRMFException, InvalidAlgorithmParameterException,
                InvalidKeyException, InvalidParameterSpecException, NoSuchAlgorithmException,
                NoSuchPaddingException, NoSuchProviderException
            {
                Cipher cipher = createCipher(encryptionAlgID.getAlgorithm());
                ASN1Object sParams = (ASN1Object)encryptionAlgID.getParameters();
                String encAlg = encryptionAlgID.getAlgorithm().getId();

                if (sParams != null && !(sParams instanceof ASN1Null))
                {
                    try
                    {
                        AlgorithmParameters params = createAlgorithmParameters(encryptionAlgID.getAlgorithm());

                        try
                        {
                            params.init(sParams.getEncoded(), "ASN.1");
                        }
                        catch (IOException e)
                        {
                            throw new CRMFException("error decoding algorithm parameters.", e);
                        }

                        cipher.init(Cipher.DECRYPT_MODE, sKey, params);
                    }
                    catch (NoSuchAlgorithmException e)
                    {
                        if (encAlg.equals(CMSEnvelopedDataGenerator.DES_EDE3_CBC)
                            || encAlg.equals(CMSEnvelopedDataGenerator.IDEA_CBC)
                            || encAlg.equals(CMSEnvelopedDataGenerator.AES128_CBC)
                            || encAlg.equals(CMSEnvelopedDataGenerator.AES192_CBC)
                            || encAlg.equals(CMSEnvelopedDataGenerator.AES256_CBC))
                        {
                            cipher.init(Cipher.DECRYPT_MODE, sKey, new IvParameterSpec(
                                ASN1OctetString.getInstance(sParams).getOctets()));
                        }
                        else
                        {
                            throw e;
                        }
                    }
                }
                else
                {
                    if (encAlg.equals(CMSEnvelopedDataGenerator.DES_EDE3_CBC)
                        || encAlg.equals(CMSEnvelopedDataGenerator.IDEA_CBC)
                        || encAlg.equals(CMSEnvelopedDataGenerator.CAST5_CBC))
                    {
                        cipher.init(Cipher.DECRYPT_MODE, sKey, new IvParameterSpec(new byte[8]));
                    }
                    else
                    {
                        cipher.init(Cipher.DECRYPT_MODE, sKey);
                    }
                }

                return cipher;
            }
        });
    }
    
    AlgorithmParameters createAlgorithmParameters(ASN1ObjectIdentifier algorithm)
        throws NoSuchAlgorithmException, NoSuchProviderException
    {
        String algorithmName = (String)BASE_CIPHER_NAMES.get(algorithm);

        if (algorithmName != null)
        {
            try
            {
                // this is reversed as the Sun policy files now allow unlimited strength RSA
                return helper.createAlgorithmParameters(algorithmName);
            }
            catch (NoSuchAlgorithmException e)
            {
                // Ignore
            }
        }
        return helper.createAlgorithmParameters(algorithm.getId());
    }
    
    KeyFactory createKeyFactory(ASN1ObjectIdentifier algorithm)
        throws CRMFException
    {
        try
        {
            String algName = (String)KEY_ALG_NAMES.get(algorithm);

            if (algName != null)
            {
                try
                {
                    // this is reversed as the Sun policy files now allow unlimited strength RSA
                    return helper.createKeyFactory(algName);
                }
                catch (NoSuchAlgorithmException e)
                {
                    // Ignore
                }
            }
            return helper.createKeyFactory(algorithm.getId());
        }
        catch (GeneralSecurityException e)
        {
            throw new CRMFException("cannot create cipher: " + e.getMessage(), e);
        }
    }

    MessageDigest createDigest(ASN1ObjectIdentifier algorithm)
        throws CRMFException
    {
        try
        {
            String digestName = (String)DIGEST_ALG_NAMES.get(algorithm);

            if (digestName != null)
            {
                try
                {
                    // this is reversed as the Sun policy files now allow unlimited strength RSA
                    return helper.createDigest(digestName);
                }
                catch (NoSuchAlgorithmException e)
                {
                    // Ignore
                }
            }
            return helper.createDigest(algorithm.getId());
        }
        catch (GeneralSecurityException e)
        {
            throw new CRMFException("cannot create cipher: " + e.getMessage(), e);
        }
    }

    Mac createMac(ASN1ObjectIdentifier algorithm)
        throws CRMFException
    {
        try
        {
            String macName = (String)MAC_ALG_NAMES.get(algorithm);

            if (macName != null)
            {
                try
                {
                    // this is reversed as the Sun policy files now allow unlimited strength RSA
                    return helper.createMac(macName);
                }
                catch (NoSuchAlgorithmException e)
                {
                    // Ignore
                }
            }
            return helper.createMac(algorithm.getId());
        }
        catch (GeneralSecurityException e)
        {
            throw new CRMFException("cannot create mac: " + e.getMessage(), e);
        }
    }

    AlgorithmParameterGenerator createAlgorithmParameterGenerator(ASN1ObjectIdentifier algorithm)
        throws GeneralSecurityException
    {
        String algorithmName = (String)BASE_CIPHER_NAMES.get(algorithm);

        if (algorithmName != null)
        {
            try
            {
                // this is reversed as the Sun policy files now allow unlimited strength RSA
                return helper.createAlgorithmParameterGenerator(algorithmName);
            }
            catch (NoSuchAlgorithmException e)
            {
                // Ignore
            }
        }
        return helper.createAlgorithmParameterGenerator(algorithm.getId());
    }

    AlgorithmParameters generateParameters(ASN1ObjectIdentifier encryptionOID, SecretKey encKey, SecureRandom rand)
        throws CRMFException
    {
        try
        {
            AlgorithmParameterGenerator pGen = createAlgorithmParameterGenerator(encryptionOID);

            if (encryptionOID.equals(CMSEnvelopedDataGenerator.RC2_CBC))
            {
                byte[]  iv = new byte[8];

                rand.nextBytes(iv);

                try
                {
                    pGen.init(new RC2ParameterSpec(encKey.getEncoded().length * 8, iv), rand);
                }
                catch (InvalidAlgorithmParameterException e)
                {
                    throw new CRMFException("parameters generation error: " + e, e);
                }
            }

            return pGen.generateParameters();
        }
        catch (NoSuchAlgorithmException e)
        {
            return null;
        }
        catch (GeneralSecurityException e)
        {
            throw new CRMFException("exception creating algorithm parameter generator: " + e, e);
        }
    }

    AlgorithmIdentifier getAlgorithmIdentifier(ASN1ObjectIdentifier encryptionOID, AlgorithmParameters params)
        throws CRMFException
    {
        DEREncodable asn1Params;
        if (params != null)
        {
            try
            {
                asn1Params = ASN1Object.fromByteArray(params.getEncoded("ASN.1"));
            }
            catch (IOException e)
            {
                throw new CRMFException("cannot encode parameters: " + e.getMessage(), e);
            }
        }
        else
        {
            asn1Params = DERNull.INSTANCE;
        }

        return new AlgorithmIdentifier(
            encryptionOID,
            asn1Params);
    }
    
    static Object execute(JCECallback callback) throws CRMFException
    {
        try
        {
            return callback.doInJCE();
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new CRMFException("can't find algorithm.", e);
        }
        catch (InvalidKeyException e)
        {
            throw new CRMFException("key invalid in message.", e);
        }
        catch (NoSuchProviderException e)
        {
            throw new CRMFException("can't find provider.", e);
        }
        catch (NoSuchPaddingException e)
        {
            throw new CRMFException("required padding not supported.", e);
        }
        catch (InvalidAlgorithmParameterException e)
        {
            throw new CRMFException("algorithm parameters invalid.", e);
        }
        catch (InvalidParameterSpecException e)
        {
            throw new CRMFException("MAC algorithm parameter spec invalid.", e);
        }
    }
    
    static interface JCECallback
    {
        Object doInJCE()
            throws CRMFException, InvalidAlgorithmParameterException, InvalidKeyException, InvalidParameterSpecException,
            NoSuchAlgorithmException, NoSuchPaddingException, NoSuchProviderException;
    }
}
