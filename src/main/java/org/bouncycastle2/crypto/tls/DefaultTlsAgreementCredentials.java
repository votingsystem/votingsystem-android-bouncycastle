package org.bouncycastle2.crypto.tls;

import org.bouncycastle2.crypto.BasicAgreement;
import org.bouncycastle2.crypto.agreement.DHBasicAgreement;
import org.bouncycastle2.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle2.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle2.crypto.params.DHPrivateKeyParameters;
import org.bouncycastle2.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle2.util.BigIntegers;

import java.math.BigInteger;

public class DefaultTlsAgreementCredentials implements TlsAgreementCredentials
{
    protected Certificate clientCert;
    protected AsymmetricKeyParameter clientPrivateKey;

    protected BasicAgreement basicAgreement;

    public DefaultTlsAgreementCredentials(Certificate clientCertificate, AsymmetricKeyParameter clientPrivateKey)
    {
        if (clientCertificate == null)
        {
            throw new IllegalArgumentException("'clientCertificate' cannot be null");
        }
        if (clientCertificate.certs.length == 0)
        {
            throw new IllegalArgumentException("'clientCertificate' cannot be empty");
        }
        if (clientPrivateKey == null)
        {
            throw new IllegalArgumentException("'clientPrivateKey' cannot be null");
        }
        if (!clientPrivateKey.isPrivate())
        {
            throw new IllegalArgumentException("'clientPrivateKey' must be private");
        }

        if (clientPrivateKey instanceof DHPrivateKeyParameters)
        {
            basicAgreement = new DHBasicAgreement();
        }
        else if (clientPrivateKey instanceof ECPrivateKeyParameters)
        {
            basicAgreement = new ECDHBasicAgreement();
        }
        else
        {
            throw new IllegalArgumentException("'clientPrivateKey' type not supported: "
                + clientPrivateKey.getClass().getName());
        }

        this.clientCert = clientCertificate;
        this.clientPrivateKey = clientPrivateKey;
    }

    public Certificate getCertificate()
    {
        return clientCert;
    }

    public byte[] generateAgreement(AsymmetricKeyParameter serverPublicKey)
    {
        basicAgreement.init(clientPrivateKey);
        BigInteger agreementValue = basicAgreement.calculateAgreement(serverPublicKey);
        return BigIntegers.asUnsignedByteArray(agreementValue);
    }
}
