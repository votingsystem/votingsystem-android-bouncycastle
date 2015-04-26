package org.bouncycastle2.cert.ocsp;

import org.bouncycastle2.asn1.*;
import org.bouncycastle2.asn1.ocsp.CertID;
import org.bouncycastle2.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle2.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle2.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle2.cert.X509CertificateHolder;
import org.bouncycastle2.operator.DigestCalculator;
import org.bouncycastle2.operator.DigestCalculatorProvider;
import org.bouncycastle2.operator.OperatorCreationException;

import java.io.OutputStream;
import java.math.BigInteger;

public class CertificateID
{
    public static final AlgorithmIdentifier HASH_SHA1 = new AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1, DERNull.INSTANCE);

    private final CertID id;

    public CertificateID(
        CertID id)
    {
        if (id == null)
        {
            throw new IllegalArgumentException("'id' cannot be null");
        }
        this.id = id;
    }

    /**
     * create from an issuer certificate and the serial number of the
     * certificate it signed.
     *
     * @param issuerCert issuing certificate
     * @param number serial number
     *
     * @exception OCSPException if any problems occur creating the id fields.
     */
    public CertificateID(
        DigestCalculator digestCalculator, X509CertificateHolder issuerCert,
        BigInteger number)
        throws OCSPException
    {
        this.id = createCertID(digestCalculator, issuerCert, new DERInteger(number));
    }

    public ASN1ObjectIdentifier getHashAlgOID()
    {
        return id.getHashAlgorithm().getAlgorithm();
    }

    public byte[] getIssuerNameHash()
    {
        return id.getIssuerNameHash().getOctets();
    }

    public byte[] getIssuerKeyHash()
    {
        return id.getIssuerKeyHash().getOctets();
    }

    /**
     * return the serial number for the certificate associated
     * with this request.
     */
    public BigInteger getSerialNumber()
    {
        return id.getSerialNumber().getValue();
    }

    public boolean matchesIssuer(X509CertificateHolder issuerCert, DigestCalculatorProvider digCalcProvider)
        throws OCSPException
    {
        try
        {
            return createCertID(digCalcProvider.get(id.getHashAlgorithm()), issuerCert, id.getSerialNumber()).equals(id);
        }
        catch (OperatorCreationException e)
        {
            throw new OCSPException("unable to create digest calculator: " + e.getMessage(), e);
        }
    }

    public CertID toASN1Object()
    {
        return id;
    }

    public boolean equals(
        Object  o)
    {
        if (!(o instanceof CertificateID))
        {
            return false;
        }

        CertificateID obj = (CertificateID)o;

        return id.getDERObject().equals(obj.id.getDERObject());
    }

    public int hashCode()
    {
        return id.getDERObject().hashCode();
    }

    /**
     * Create a new CertificateID for a new serial number derived from a previous one
     * calculated for the same CA certificate.
     *
     * @param original the previously calculated CertificateID for the CA.
     * @param newSerialNumber the serial number for the new certificate of interest.
     *
     * @return a new CertificateID for newSerialNumber
     */
    public static CertificateID deriveCertificateID(CertificateID original, BigInteger newSerialNumber)
    {
        return new CertificateID(new CertID(original.id.getHashAlgorithm(), original.id.getIssuerNameHash(), original.id.getIssuerKeyHash(), new DERInteger(newSerialNumber)));
    }

    private static CertID createCertID(DigestCalculator digCalc, X509CertificateHolder issuerCert, DERInteger serialNumber)
        throws OCSPException
    {
        try
        {
            OutputStream dgOut = digCalc.getOutputStream();

            dgOut.write(issuerCert.toASN1Structure().getSubject().getDEREncoded());
            dgOut.close();

            ASN1OctetString issuerNameHash = new DEROctetString(digCalc.getDigest());

            SubjectPublicKeyInfo info = issuerCert.getSubjectPublicKeyInfo();

            dgOut = digCalc.getOutputStream();

            dgOut.write(info.getPublicKeyData().getBytes());
            dgOut.close();

            ASN1OctetString issuerKeyHash = new DEROctetString(digCalc.getDigest());

            return new CertID(digCalc.getAlgorithmIdentifier(), issuerNameHash, issuerKeyHash, serialNumber);
        }
        catch (Exception e)
        {
            throw new OCSPException("problem creating ID: " + e, e);
        }
    }
}
