package org.bouncycastle2.asn1.esf;

import org.bouncycastle2.asn1.*;
import org.bouncycastle2.asn1.x509.AlgorithmIdentifier;

public class OtherHashAlgAndValue
    extends ASN1Encodable
{
    private AlgorithmIdentifier hashAlgorithm;
    private ASN1OctetString     hashValue;


    public static OtherHashAlgAndValue getInstance(
        Object obj)
    {
        if (obj instanceof OtherHashAlgAndValue)
        {
            return (OtherHashAlgAndValue) obj;
        }
        else if (obj != null)
        {
            return new OtherHashAlgAndValue(ASN1Sequence.getInstance(obj));
        }

        throw new IllegalArgumentException("null value in getInstance");
    }

    private OtherHashAlgAndValue(
        ASN1Sequence seq)
    {
        if (seq.size() != 2)
        {
            throw new IllegalArgumentException("Bad sequence size: " + seq.size());
        }

        hashAlgorithm = AlgorithmIdentifier.getInstance(seq.getObjectAt(0));
        hashValue = ASN1OctetString.getInstance(seq.getObjectAt(1));
    }

    public OtherHashAlgAndValue(
        AlgorithmIdentifier hashAlgorithm,
        ASN1OctetString     hashValue)
    {
        this.hashAlgorithm = hashAlgorithm;
        this.hashValue = hashValue;
    }

    public AlgorithmIdentifier getHashAlgorithm()
    {
        return hashAlgorithm;
    }

    public ASN1OctetString getHashValue()
    {
        return hashValue;
    }

    /**
     * <pre>
     * OtherHashAlgAndValue ::= SEQUENCE {
     *     hashAlgorithm AlgorithmIdentifier,
     *     hashValue OtherHashValue }
     *
     * OtherHashValue ::= OCTET STRING
     * </pre>
     */
    public DERObject toASN1Object()
    {
        ASN1EncodableVector v = new ASN1EncodableVector();

        v.add(hashAlgorithm);
        v.add(hashValue);

        return new DERSequence(v);
    }
}
