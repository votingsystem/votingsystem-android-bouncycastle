package org.bouncycastle2.cms.jcajce;

import org.bouncycastle2.asn1.ASN1Object;
import org.bouncycastle2.asn1.ASN1ObjectIdentifier;
import org.bouncycastle2.asn1.ASN1OctetString;
import org.bouncycastle2.asn1.cms.ecc.MQVuserKeyingMaterial;
import org.bouncycastle2.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle2.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle2.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle2.cms.CMSEnvelopedGenerator;
import org.bouncycastle2.cms.CMSException;
import org.bouncycastle2.cms.KeyAgreeRecipient;
import org.bouncycastle2.jcajce.DefaultJcaJceHelper;
import org.bouncycastle2.jcajce.NamedJcaJceHelper;
import org.bouncycastle2.jcajce.ProviderJcaJceHelper;
import org.bouncycastle2.jce.spec.MQVPrivateKeySpec;
import org.bouncycastle2.jce.spec.MQVPublicKeySpec;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

public abstract class JceKeyAgreeRecipient
    implements KeyAgreeRecipient
{
    private PrivateKey recipientKey;
    protected EnvelopedDataHelper helper = new EnvelopedDataHelper(new DefaultJcaJceHelper());
    protected EnvelopedDataHelper contentHelper = helper;

    public JceKeyAgreeRecipient(PrivateKey recipientKey)
    {
        this.recipientKey = recipientKey;
    }

    /**
     * Set the provider to use for key recovery and content processing.
     *
     * @param provider provider to use.
     * @return this recipient.
     */
    public JceKeyAgreeRecipient setProvider(Provider provider)
    {
        this.helper = new EnvelopedDataHelper(new ProviderJcaJceHelper(provider));
        this.contentHelper = helper;

        return this;
    }

    /**
     * Set the provider to use for key recovery and content processing.
     *
     * @param providerName the name of the provider to use.
     * @return this recipient.
     */
    public JceKeyAgreeRecipient setProvider(String providerName)
    {
        this.helper = new EnvelopedDataHelper(new NamedJcaJceHelper(providerName));
        this.contentHelper = helper;

        return this;
    }

    /**
     * Set the provider to use for content processing.
     *
     * @param provider the provider to use.
     * @return this recipient.
     */
    public JceKeyAgreeRecipient setContentProvider(Provider provider)
    {
        this.contentHelper = new EnvelopedDataHelper(new ProviderJcaJceHelper(provider));

        return this;
    }

    /**
     * Set the provider to use for content processing.
     *
     * @param providerName the name of the provider to use.
     * @return this recipient.
     */
    public JceKeyAgreeRecipient setContentProvider(String providerName)
    {
        this.contentHelper = new EnvelopedDataHelper(new NamedJcaJceHelper(providerName));

        return this;
    }

    private SecretKey calculateAgreedWrapKey(AlgorithmIdentifier keyEncAlg, ASN1ObjectIdentifier wrapAlg,
        PublicKey senderPublicKey, ASN1OctetString userKeyingMaterial, PrivateKey receiverPrivateKey)
        throws CMSException, GeneralSecurityException, IOException
    {
        String agreeAlg = keyEncAlg.getAlgorithm().getId();

        if (agreeAlg.equals(CMSEnvelopedGenerator.ECMQV_SHA1KDF))
        {
            byte[] ukmEncoding = userKeyingMaterial.getOctets();
            MQVuserKeyingMaterial ukm = MQVuserKeyingMaterial.getInstance(
                ASN1Object.fromByteArray(ukmEncoding));

            SubjectPublicKeyInfo pubInfo = new SubjectPublicKeyInfo(
                                                getPrivateKeyAlgorithmIdentifier(),
                                                ukm.getEphemeralPublicKey().getPublicKey().getBytes());

            X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(pubInfo.getEncoded());
            KeyFactory fact = helper.createKeyFactory(keyEncAlg.getAlgorithm());
            PublicKey ephemeralKey = fact.generatePublic(pubSpec);

            senderPublicKey = new MQVPublicKeySpec(senderPublicKey, ephemeralKey);
            receiverPrivateKey = new MQVPrivateKeySpec(receiverPrivateKey, receiverPrivateKey);
        }

        KeyAgreement agreement = helper.createKeyAgreement(keyEncAlg.getAlgorithm());

        agreement.init(receiverPrivateKey);
        agreement.doPhase(senderPublicKey, true);

        return agreement.generateSecret(wrapAlg.getId());
    }

    private Key unwrapSessionKey(ASN1ObjectIdentifier wrapAlg, SecretKey agreedKey, ASN1ObjectIdentifier contentEncryptionAlgorithm, byte[] encryptedContentEncryptionKey)
        throws CMSException, InvalidKeyException, NoSuchAlgorithmException
    {
        Cipher keyCipher = helper.createCipher(wrapAlg);
        keyCipher.init(Cipher.UNWRAP_MODE, agreedKey);
        return keyCipher.unwrap(encryptedContentEncryptionKey, helper.getBaseCipherName(contentEncryptionAlgorithm), Cipher.SECRET_KEY);
    }

    protected Key extractSecretKey(AlgorithmIdentifier keyEncryptionAlgorithm, AlgorithmIdentifier contentEncryptionAlgorithm, SubjectPublicKeyInfo senderKey, ASN1OctetString userKeyingMaterial, byte[] encryptedContentEncryptionKey)
        throws CMSException
    {
        try
        {
            ASN1ObjectIdentifier wrapAlg =
                AlgorithmIdentifier.getInstance(keyEncryptionAlgorithm.getParameters()).getAlgorithm();

            X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(senderKey.getEncoded());
            KeyFactory fact = helper.createKeyFactory(keyEncryptionAlgorithm.getAlgorithm());
            PublicKey senderPublicKey = fact.generatePublic(pubSpec);

            SecretKey agreedWrapKey = calculateAgreedWrapKey(keyEncryptionAlgorithm, wrapAlg,
                senderPublicKey, userKeyingMaterial, recipientKey);

            return unwrapSessionKey(wrapAlg, agreedWrapKey, contentEncryptionAlgorithm.getAlgorithm(), encryptedContentEncryptionKey);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new CMSException("can't find algorithm.", e);
        }
        catch (InvalidKeyException e)
        {
            throw new CMSException("key invalid in message.", e);
        }
        catch (InvalidKeySpecException e)
        {
            throw new CMSException("originator key spec invalid.", e);
        }
        catch (NoSuchPaddingException e)
        {
            throw new CMSException("required padding not supported.", e);
        }
        catch (Exception e)
        {
            throw new CMSException("originator key invalid.", e);
        }
    }

    public AlgorithmIdentifier getPrivateKeyAlgorithmIdentifier()
    {
        return PrivateKeyInfo.getInstance(recipientKey.getEncoded()).getAlgorithmId();
    }
}
