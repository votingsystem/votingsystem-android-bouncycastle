package org.bouncycastle2.cms.jcajce;

import org.bouncycastle2.asn1.ASN1ObjectIdentifier;
import org.bouncycastle2.asn1.ASN1OctetString;
import org.bouncycastle2.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle2.cms.CMSException;
import org.bouncycastle2.cms.PasswordRecipientInfoGenerator;
import org.bouncycastle2.jcajce.DefaultJcaJceHelper;
import org.bouncycastle2.jcajce.NamedJcaJceHelper;
import org.bouncycastle2.jcajce.ProviderJcaJceHelper;
import org.bouncycastle2.operator.GenericKey;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.Provider;

public class JcePasswordRecipientInfoGenerator
    extends PasswordRecipientInfoGenerator
{
    private EnvelopedDataHelper helper = new EnvelopedDataHelper(new DefaultJcaJceHelper());

    public JcePasswordRecipientInfoGenerator(ASN1ObjectIdentifier kekAlgorithm, char[] password)
    {
        super(kekAlgorithm, password);
    }

    public JcePasswordRecipientInfoGenerator setProvider(Provider provider)
    {
        this.helper = new EnvelopedDataHelper(new ProviderJcaJceHelper(provider));

        return this;
    }

    public JcePasswordRecipientInfoGenerator setProvider(String providerName)
    {
        this.helper = new EnvelopedDataHelper(new NamedJcaJceHelper(providerName));

        return this;
    }

    public byte[] generateEncryptedBytes(AlgorithmIdentifier keyEncryptionAlgorithm, byte[] derivedKey, GenericKey contentEncryptionKey)
        throws CMSException
    {
        Key contentEncryptionKeySpec = CMSUtils.getJceKey(contentEncryptionKey);
        Cipher keyEncryptionCipher = helper.createRFC3211Wrapper(keyEncryptionAlgorithm.getAlgorithm());

        try
        {
            IvParameterSpec ivSpec = new IvParameterSpec(ASN1OctetString.getInstance(keyEncryptionAlgorithm.getParameters()).getOctets());

            keyEncryptionCipher.init(Cipher.WRAP_MODE, new SecretKeySpec(derivedKey, keyEncryptionCipher.getAlgorithm()), ivSpec);

            return keyEncryptionCipher.wrap(contentEncryptionKeySpec);
        }
        catch (GeneralSecurityException e)
        {
            throw new CMSException("cannot process content encryption key: " + e.getMessage(), e);
        }
    }
}