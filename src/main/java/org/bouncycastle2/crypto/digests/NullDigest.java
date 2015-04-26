package org.bouncycastle2.crypto.digests;

import org.bouncycastle2.crypto.Digest;

import java.io.ByteArrayOutputStream;


public class NullDigest
    implements Digest
{
    private ByteArrayOutputStream bOut = new ByteArrayOutputStream();

    public String getAlgorithmName()
    {
        return "NULL";
    }

    public int getDigestSize()
    {
        return bOut.size();
    }

    public void update(byte in)
    {
        bOut.write(in);
    }

    public void update(byte[] in, int inOff, int len)
    {
        bOut.write(in, inOff, len);
    }

    public int doFinal(byte[] out, int outOff)
    {
        byte[] res = bOut.toByteArray();

        System.arraycopy(res, 0, out, outOff, res.length);

        reset();
        
        return res.length;
    }

    public void reset()
    {
        bOut.reset();
    }
}