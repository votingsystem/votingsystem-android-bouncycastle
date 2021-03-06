package org.bouncycastle.tsp.cms;

import org.bouncycastle2.asn1.ASN1String;
import org.bouncycastle2.asn1.cms.Attributes;
import org.bouncycastle2.asn1.cms.MetaData;
import org.bouncycastle2.cms.CMSException;
import org.bouncycastle2.operator.DigestCalculator;

import java.io.IOException;

class MetaDataUtil
{
    private final MetaData          metaData;

    MetaDataUtil(MetaData metaData)
    {
        this.metaData = metaData;
    }

    void initialiseMessageImprintDigestCalculator(DigestCalculator calculator)
        throws CMSException
    {
        if (metaData != null && metaData.isHashProtected())
        {
            try
            {
                calculator.getOutputStream().write(metaData.getDEREncoded());
            }
            catch (IOException e)
            {
                throw new CMSException("unable to initialise calculator from metaData: " + e.getMessage(), e);
            }
        }
    }

    String getFileName()
    {
        if (metaData != null)
        {
            return convertString(metaData.getFileName());
        }

        return null;
    }

    String getMediaType()
    {
        if (metaData != null)
        {
            return convertString(metaData.getMediaType());
        }

        return null;
    }

    Attributes getOtherMetaData()
    {
        if (metaData != null)
        {
            return metaData.getOtherMetaData();
        }

        return null;
    }

    private String convertString(ASN1String s)
    {
        if (s != null)
        {
            return s.toString();
        }

        return null;
    }
}
