package org.bouncycastle2.cms;

import org.bouncycastle2.asn1.*;
import org.bouncycastle2.asn1.cms.*;
import org.bouncycastle2.asn1.cms.ecc.MQVuserKeyingMaterial;
import org.bouncycastle2.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle2.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle2.jce.spec.MQVPrivateKeySpec;
import org.bouncycastle2.jce.spec.MQVPublicKeySpec;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

class KeyAgreeIntRecipientInfoGenerator
    implements IntRecipientInfoGenerator
{
    private DERObjectIdentifier keyAgreementOID;
    private DERObjectIdentifier keyEncryptionOID;
    private ArrayList recipientCerts;
    private KeyPair senderKeyPair;

    KeyAgreeIntRecipientInfoGenerator()
    {
    }

    void setKeyAgreementOID(DERObjectIdentifier keyAgreementOID)
    {
        this.keyAgreementOID = keyAgreementOID;
    }

    void setKeyEncryptionOID(DERObjectIdentifier keyEncryptionOID)
    {
        this.keyEncryptionOID = keyEncryptionOID;
    }

    void setRecipientCerts(Collection recipientCerts)
    {
        this.recipientCerts = new ArrayList(recipientCerts);
    }

    void setSenderKeyPair(KeyPair senderKeyPair)
    {
        this.senderKeyPair = senderKeyPair;
    }

    public RecipientInfo generate(SecretKey contentEncryptionKey, SecureRandom random,
            Provider prov) throws GeneralSecurityException
    {
        PublicKey senderPublicKey = senderKeyPair.getPublic();
        PrivateKey senderPrivateKey = senderKeyPair.getPrivate();


        OriginatorIdentifierOrKey originator;
        try
        {
            originator = new OriginatorIdentifierOrKey(
                createOriginatorPublicKey(senderPublicKey));
        }
        catch (IOException e)
        {
            throw new InvalidKeyException("cannot extract originator public key: " + e);
        }


        ASN1OctetString ukm = null;
        if (keyAgreementOID.getId().equals(CMSEnvelopedGenerator.ECMQV_SHA1KDF))
        {
            try
            {
                ECParameterSpec ecParamSpec = ((ECPublicKey)senderPublicKey).getParams();

                KeyPairGenerator ephemKPG = KeyPairGenerator.getInstance(
                    keyAgreementOID.getId(), prov);
                ephemKPG.initialize(ecParamSpec, random);

                KeyPair ephemKP = ephemKPG.generateKeyPair();

                ukm = new DEROctetString(
                    new MQVuserKeyingMaterial(
                        createOriginatorPublicKey(ephemKP.getPublic()), null));

                senderPrivateKey = new MQVPrivateKeySpec(
                    senderPrivateKey, ephemKP.getPrivate(), ephemKP.getPublic());
            }
            catch (InvalidAlgorithmParameterException e)
            {
                throw new InvalidKeyException(
                    "cannot determine MQV ephemeral key pair parameters from public key: " + e);
            }
            catch (IOException e)
            {
                throw new InvalidKeyException("cannot extract MQV ephemeral public key: " + e);
            }
        }


        ASN1EncodableVector params = new ASN1EncodableVector();
        params.add(keyEncryptionOID);
        params.add(DERNull.INSTANCE);
        AlgorithmIdentifier keyEncAlg = new AlgorithmIdentifier(keyAgreementOID,
            new DERSequence(params));


        ASN1EncodableVector recipientEncryptedKeys = new ASN1EncodableVector();
        Iterator it = recipientCerts.iterator();
        while (it.hasNext())
        {
            X509Certificate recipientCert = (X509Certificate)it.next();

            // TODO Should there be a SubjectKeyIdentifier-based alternative?
            KeyAgreeRecipientIdentifier karid = new KeyAgreeRecipientIdentifier(
                CMSUtils.getIssuerAndSerialNumber(recipientCert));

            PublicKey recipientPublicKey = recipientCert.getPublicKey();

            if (keyAgreementOID.getId().equals(CMSEnvelopedGenerator.ECMQV_SHA1KDF))
            {
                recipientPublicKey = new MQVPublicKeySpec(recipientPublicKey, recipientPublicKey);
            }

            // Use key agreement to choose a wrap key for this recipient
            KeyAgreement keyAgreement = KeyAgreement.getInstance(keyAgreementOID.getId(), prov);
            keyAgreement.init(senderPrivateKey, random);
            keyAgreement.doPhase(recipientPublicKey, true);
            SecretKey keyEncryptionKey = keyAgreement.generateSecret(keyEncryptionOID.getId());

            // Wrap the content encryption key with the agreement key
            Cipher keyEncryptionCipher = CMSEnvelopedHelper.INSTANCE.createSymmetricCipher(
                keyEncryptionOID.getId(), prov);
            keyEncryptionCipher.init(Cipher.WRAP_MODE, keyEncryptionKey, random);
            byte[] encryptedKeyBytes = keyEncryptionCipher.wrap(contentEncryptionKey);

            ASN1OctetString encryptedKey = new DEROctetString(encryptedKeyBytes);

            recipientEncryptedKeys.add(new RecipientEncryptedKey(karid, encryptedKey));
        }

        return new RecipientInfo(new KeyAgreeRecipientInfo(originator, ukm,
                keyEncAlg, new DERSequence(recipientEncryptedKeys)));
    }

    // TODO Make this a public helper?
    private static OriginatorPublicKey createOriginatorPublicKey(PublicKey publicKey)
        throws IOException
    {
        SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(
            ASN1Object.fromByteArray(publicKey.getEncoded()));
        return new OriginatorPublicKey(
            new AlgorithmIdentifier(spki.getAlgorithmId().getObjectId(), DERNull.INSTANCE),
            spki.getPublicKeyData().getBytes());
    }
}
