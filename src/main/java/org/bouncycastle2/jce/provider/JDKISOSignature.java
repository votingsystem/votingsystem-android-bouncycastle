package org.bouncycastle2.jce.provider;

import org.bouncycastle2.crypto.AsymmetricBlockCipher;
import org.bouncycastle2.crypto.CipherParameters;
import org.bouncycastle2.crypto.Digest;
import org.bouncycastle2.crypto.digests.MD5Digest;
import org.bouncycastle2.crypto.digests.RIPEMD160Digest;
import org.bouncycastle2.crypto.digests.SHA1Digest;
import org.bouncycastle2.crypto.engines.RSABlindedEngine;
import org.bouncycastle2.crypto.signers.ISO9796d2Signer;

import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.AlgorithmParameterSpec;

public class JDKISOSignature
    extends SignatureSpi
{
    private ISO9796d2Signer         signer;

    protected JDKISOSignature(
        Digest digest,
        AsymmetricBlockCipher cipher)
    {
        signer = new ISO9796d2Signer(cipher, digest, true);
    }

    protected void engineInitVerify(
        PublicKey   publicKey)
        throws InvalidKeyException
    {
        CipherParameters    param = RSAUtil.generatePublicKeyParameter((RSAPublicKey)publicKey);

        signer.init(false, param);
    }

    protected void engineInitSign(
        PrivateKey  privateKey)
        throws InvalidKeyException
    {
        CipherParameters    param = RSAUtil.generatePrivateKeyParameter((RSAPrivateKey)privateKey);

        signer.init(true, param);
    }

    protected void engineUpdate(
        byte    b)
        throws SignatureException
    {
        signer.update(b);
    }

    protected void engineUpdate(
        byte[]  b,
        int     off,
        int     len) 
        throws SignatureException
    {
        signer.update(b, off, len);
    }

    protected byte[] engineSign()
        throws SignatureException
    {
        try
        {
            byte[]  sig = signer.generateSignature();

            return sig;
        }
        catch (Exception e)
        {
            throw new SignatureException(e.toString());
        }
    }

    protected boolean engineVerify(
        byte[]  sigBytes) 
        throws SignatureException
    {
        boolean yes = signer.verifySignature(sigBytes);

        return yes;
    }

    protected void engineSetParameter(
        AlgorithmParameterSpec params)
    {
        throw new UnsupportedOperationException("engineSetParameter unsupported");
    }

    /**
     * @deprecated replaced with <a href = "#engineSetParameter(java.security.spec.AlgorithmParameterSpec)">
     */
    protected void engineSetParameter(
        String  param,
        Object  value)
    {
        throw new UnsupportedOperationException("engineSetParameter unsupported");
    }

    /**
     * @deprecated
     */
    protected Object engineGetParameter(
        String      param)
    {
        throw new UnsupportedOperationException("engineSetParameter unsupported");
    }

    static public class SHA1WithRSAEncryption
        extends JDKISOSignature
    {
        public SHA1WithRSAEncryption()
        {
            super(new SHA1Digest(), new RSABlindedEngine());
        }
    }

    static public class MD5WithRSAEncryption
        extends JDKISOSignature
    {
        public MD5WithRSAEncryption()
        {
            super(new MD5Digest(), new RSABlindedEngine());
        }
    }

    static public class RIPEMD160WithRSAEncryption
        extends JDKISOSignature
    {
        public RIPEMD160WithRSAEncryption()
        {
            super(new RIPEMD160Digest(), new RSABlindedEngine());
        }
    }
}
