package org.bouncycastle2.cms.jcajce;

import org.bouncycastle2.asn1.*;
import org.bouncycastle2.asn1.cms.KeyAgreeRecipientIdentifier;
import org.bouncycastle2.asn1.cms.RecipientEncryptedKey;
import org.bouncycastle2.asn1.cms.RecipientKeyIdentifier;
import org.bouncycastle2.asn1.cms.ecc.MQVuserKeyingMaterial;
import org.bouncycastle2.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle2.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle2.cms.CMSAlgorithm;
import org.bouncycastle2.cms.CMSEnvelopedGenerator;
import org.bouncycastle2.cms.CMSException;
import org.bouncycastle2.cms.KeyAgreeRecipientInfoGenerator;
import org.bouncycastle2.jcajce.DefaultJcaJceHelper;
import org.bouncycastle2.jcajce.NamedJcaJceHelper;
import org.bouncycastle2.jcajce.ProviderJcaJceHelper;
import org.bouncycastle2.jce.spec.MQVPrivateKeySpec;
import org.bouncycastle2.jce.spec.MQVPublicKeySpec;
import org.bouncycastle2.operator.GenericKey;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.util.ArrayList;
import java.util.List;

public class JceKeyAgreeRecipientInfoGenerator
    extends KeyAgreeRecipientInfoGenerator
{
    private List recipientIDs = new ArrayList();
    private List recipientKeys = new ArrayList();
    private PublicKey senderPublicKey;
    private PrivateKey senderPrivateKey;

    private EnvelopedDataHelper helper = new EnvelopedDataHelper(new DefaultJcaJceHelper());
    private SecureRandom random;
    private KeyPair ephemeralKP;

    public JceKeyAgreeRecipientInfoGenerator(ASN1ObjectIdentifier keyAgreementOID, PrivateKey senderPrivateKey, PublicKey senderPublicKey, ASN1ObjectIdentifier keyEncryptionOID)
        throws CMSException
    {
        super(keyAgreementOID, SubjectPublicKeyInfo.getInstance(senderPublicKey.getEncoded()), keyEncryptionOID);

        this.senderPublicKey = senderPublicKey;
        this.senderPrivateKey = senderPrivateKey;
    }

    public JceKeyAgreeRecipientInfoGenerator setProvider(Provider provider)
    {
        this.helper = new EnvelopedDataHelper(new ProviderJcaJceHelper(provider));

        return this;
    }

    public JceKeyAgreeRecipientInfoGenerator setProvider(String providerName)
    {
        this.helper = new EnvelopedDataHelper(new NamedJcaJceHelper(providerName));

        return this;
    }

    public JceKeyAgreeRecipientInfoGenerator setSecureRandom(SecureRandom random)
    {
        this.random = random;

        return this;
    }

    /**
     * Add a recipient based on the passed in certificate's public key and its issuer and serial number.
     * 
     * @param recipientCert recipient's certificate
     * @return the current instance.
     * @throws CertificateEncodingException  if the necessary data cannot be extracted from the certificate.
     */
    public JceKeyAgreeRecipientInfoGenerator addRecipient(X509Certificate recipientCert)
        throws CertificateEncodingException
    {
        recipientIDs.add(new KeyAgreeRecipientIdentifier(CMSUtils.getIssuerAndSerialNumber(recipientCert)));
        recipientKeys.add(recipientCert.getPublicKey());

        return this;
    }

    /**
     * Add a recipient identified by the passed in subjectKeyID and the for the passed in public key.
     *
     * @param subjectKeyID identifier actual recipient will use to match the private key.
     * @param publicKey the public key for encrypting the secret key.
     * @return the current instance.
     * @throws CertificateEncodingException
     */
    public JceKeyAgreeRecipientInfoGenerator addRecipient(byte[] subjectKeyID, PublicKey publicKey)
        throws CertificateEncodingException
    {
        recipientIDs.add(new KeyAgreeRecipientIdentifier(new RecipientKeyIdentifier(subjectKeyID)));
        recipientKeys.add(publicKey);

        return this;
    }

    public ASN1Sequence generateRecipientEncryptedKeys(AlgorithmIdentifier keyAgreeAlgorithm, AlgorithmIdentifier keyEncryptionAlgorithm, GenericKey contentEncryptionKey)
        throws CMSException
    {
        init(keyAgreeAlgorithm.getAlgorithm());

        PrivateKey senderPrivateKey = this.senderPrivateKey;

        ASN1ObjectIdentifier keyAgreementOID = keyAgreeAlgorithm.getAlgorithm();

        if (keyAgreementOID.getId().equals(CMSEnvelopedGenerator.ECMQV_SHA1KDF))
        {           
            senderPrivateKey = new MQVPrivateKeySpec(
                senderPrivateKey, ephemeralKP.getPrivate(), ephemeralKP.getPublic());
        }

        ASN1EncodableVector recipientEncryptedKeys = new ASN1EncodableVector();
        for (int i = 0; i != recipientIDs.size(); i++)
        {
            PublicKey recipientPublicKey = (PublicKey)recipientKeys.get(i);
            KeyAgreeRecipientIdentifier karId = (KeyAgreeRecipientIdentifier)recipientIDs.get(i);

            if (keyAgreementOID.getId().equals(CMSEnvelopedGenerator.ECMQV_SHA1KDF))
            {
                recipientPublicKey = new MQVPublicKeySpec(recipientPublicKey, recipientPublicKey);
            }

            try
            {
                // Use key agreement to choose a wrap key for this recipient
                KeyAgreement keyAgreement = helper.createKeyAgreement(keyAgreementOID);
                keyAgreement.init(senderPrivateKey, random);
                keyAgreement.doPhase(recipientPublicKey, true);
                SecretKey keyEncryptionKey = keyAgreement.generateSecret(keyEncryptionAlgorithm.getAlgorithm().getId());

                // Wrap the content encryption key with the agreement key
                Cipher keyEncryptionCipher = helper.createCipher(keyEncryptionAlgorithm.getAlgorithm());

                keyEncryptionCipher.init(Cipher.WRAP_MODE, keyEncryptionKey, random);

                byte[] encryptedKeyBytes = keyEncryptionCipher.wrap(CMSUtils.getJceKey(contentEncryptionKey));

                ASN1OctetString encryptedKey = new DEROctetString(encryptedKeyBytes);

                recipientEncryptedKeys.add(new RecipientEncryptedKey(karId, encryptedKey));
            }
            catch (GeneralSecurityException e)
            {
                throw new CMSException("cannot perform agreement step: " + e.getMessage(), e);
            }
        }

        return new DERSequence(recipientEncryptedKeys);
    }

    protected ASN1Encodable getUserKeyingMaterial(AlgorithmIdentifier keyAgreeAlg)
        throws CMSException
    {
        init(keyAgreeAlg.getAlgorithm());

        if (ephemeralKP != null)
        {
            return new MQVuserKeyingMaterial(
                        createOriginatorPublicKey(SubjectPublicKeyInfo.getInstance(ephemeralKP.getPublic().getEncoded())), null);
        }

        return null;
    }

    private void init(ASN1ObjectIdentifier keyAgreementOID)
        throws CMSException
    {
        if (random == null)
        {
            random = new SecureRandom();
        }

        if (keyAgreementOID.equals(CMSAlgorithm.ECMQV_SHA1KDF))
        {
            if (ephemeralKP == null)
            {
                try
                {
                    ECParameterSpec ecParamSpec = ((ECPublicKey)senderPublicKey).getParams();

                    KeyPairGenerator ephemKPG = helper.createKeyPairGenerator(keyAgreementOID);

                    ephemKPG.initialize(ecParamSpec, random);

                    ephemeralKP = ephemKPG.generateKeyPair();
                }
                catch (InvalidAlgorithmParameterException e)
                {
                    throw new CMSException(
                        "cannot determine MQV ephemeral key pair parameters from public key: " + e);
                }
            }
        }
    }
}