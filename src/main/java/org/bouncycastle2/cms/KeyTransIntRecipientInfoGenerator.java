package org.bouncycastle2.cms;

import org.bouncycastle2.asn1.ASN1Object;
import org.bouncycastle2.asn1.ASN1OctetString;
import org.bouncycastle2.asn1.DEROctetString;
import org.bouncycastle2.asn1.cms.IssuerAndSerialNumber;
import org.bouncycastle2.asn1.cms.KeyTransRecipientInfo;
import org.bouncycastle2.asn1.cms.RecipientIdentifier;
import org.bouncycastle2.asn1.cms.RecipientInfo;
import org.bouncycastle2.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle2.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle2.asn1.x509.TBSCertificateStructure;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.*;
import java.security.cert.X509Certificate;

class KeyTransIntRecipientInfoGenerator
    implements IntRecipientInfoGenerator
{
    // TODO Pass recipId, keyEncAlg instead?
    private TBSCertificateStructure recipientTBSCert;
    private PublicKey recipientPublicKey;
    private ASN1OctetString subjectKeyIdentifier;

    // Derived fields
    private SubjectPublicKeyInfo info;

    KeyTransIntRecipientInfoGenerator()
    {
    }

    void setRecipientCert(X509Certificate recipientCert)
    {
        this.recipientTBSCert = CMSUtils.getTBSCertificateStructure(recipientCert);
        this.recipientPublicKey = recipientCert.getPublicKey();
        this.info = recipientTBSCert.getSubjectPublicKeyInfo();
    }

    void setRecipientPublicKey(PublicKey recipientPublicKey)
    {
        this.recipientPublicKey = recipientPublicKey;

        try
        {
            info = SubjectPublicKeyInfo.getInstance(
                ASN1Object.fromByteArray(recipientPublicKey.getEncoded()));
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException(
                    "can't extract key algorithm from this key");
        }
    }

    void setSubjectKeyIdentifier(ASN1OctetString subjectKeyIdentifier)
    {
        this.subjectKeyIdentifier = subjectKeyIdentifier;
    }

    public RecipientInfo generate(SecretKey contentEncryptionKey, SecureRandom random,
            Provider prov) throws GeneralSecurityException
    {
        AlgorithmIdentifier keyEncryptionAlgorithm = info.getAlgorithmId();

        byte[] encryptedKeyBytes = null;

        Cipher keyEncryptionCipher = CMSEnvelopedHelper.INSTANCE.createAsymmetricCipher(
            keyEncryptionAlgorithm.getObjectId().getId(), prov);

        try
        {
            keyEncryptionCipher.init(Cipher.WRAP_MODE, recipientPublicKey, random);
            encryptedKeyBytes = keyEncryptionCipher.wrap(contentEncryptionKey);
        }
        catch (GeneralSecurityException e)
        {
        }
        catch (IllegalStateException e)
        {
        }
        catch (UnsupportedOperationException e)
        {
        }
        catch (ProviderException e)   
        {
        }

        // some providers do not support WRAP (this appears to be only for asymmetric algorithms) 
        if (encryptedKeyBytes == null)
        {
            keyEncryptionCipher.init(Cipher.ENCRYPT_MODE, recipientPublicKey, random);
            encryptedKeyBytes = keyEncryptionCipher.doFinal(contentEncryptionKey.getEncoded());
        }

        RecipientIdentifier recipId;
        if (recipientTBSCert != null)
        {
            IssuerAndSerialNumber issuerAndSerial = new IssuerAndSerialNumber(
                recipientTBSCert.getIssuer(), recipientTBSCert.getSerialNumber().getValue());
            recipId = new RecipientIdentifier(issuerAndSerial);
        }
        else
        {
            recipId = new RecipientIdentifier(subjectKeyIdentifier);
        }

        return new RecipientInfo(new KeyTransRecipientInfo(recipId, keyEncryptionAlgorithm,
            new DEROctetString(encryptedKeyBytes)));
    }
}
