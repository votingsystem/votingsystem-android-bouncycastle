package org.bouncycastle2.cms;

import org.bouncycastle2.asn1.cms.KEKIdentifier;
import org.bouncycastle2.asn1.cms.KEKRecipientInfo;
import org.bouncycastle2.asn1.x509.AlgorithmIdentifier;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.*;

/**
 * the RecipientInfo class for a recipient who has been sent a message
 * encrypted using a secret key known to the other side.
 */
public class KEKRecipientInformation
    extends RecipientInformation
{
    private KEKRecipientInfo      info;

    KEKRecipientInformation(
        KEKRecipientInfo        info,
        AlgorithmIdentifier     messageAlgorithm,
        CMSSecureReadable       secureReadable,
        AuthAttributesProvider  additionalData)
    {
        super(info.getKeyEncryptionAlgorithm(), messageAlgorithm, secureReadable, additionalData);

        this.info = info;

        KEKIdentifier kekId = info.getKekid();

        this.rid = new KEKRecipientId(kekId.getKeyIdentifier().getOctets());
    }

    /**
     * decrypt the content and return an input stream.
     */
    public CMSTypedStream getContentStream(
        Key      key,
        String   prov)
        throws CMSException, NoSuchProviderException
    {
        return getContentStream(key, CMSUtils.getProvider(prov));
    }

    /**
     * decrypt the content and return an input stream.
     */
    public CMSTypedStream getContentStream(
        Key      key,
        Provider prov)
        throws CMSException
    {
        try
        {
            Cipher keyCipher = CMSEnvelopedHelper.INSTANCE.createSymmetricCipher(
                keyEncAlg.getObjectId().getId(), prov);
            keyCipher.init(Cipher.UNWRAP_MODE, key);
            Key sKey = keyCipher.unwrap(info.getEncryptedKey().getOctets(), getContentAlgorithmName(),
                Cipher.SECRET_KEY);

            return getContentFromSessionKey(sKey, prov);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new CMSException("can't find algorithm.", e);
        }
        catch (InvalidKeyException e)
        {
            throw new CMSException("key invalid in message.", e);
        }
        catch (NoSuchPaddingException e)
        {
            throw new CMSException("required padding not supported.", e);
        }
    }

    protected RecipientOperator getRecipientOperator(Recipient recipient)
        throws CMSException, IOException
    {
        return ((KEKRecipient)recipient).getRecipientOperator(keyEncAlg, messageAlgorithm, info.getEncryptedKey().getOctets());
    }
}
