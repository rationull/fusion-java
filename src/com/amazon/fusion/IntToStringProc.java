// Copyright (c) 2013-2018 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.FusionNumber.checkNullableIntArg;
import static com.amazon.fusion.FusionString.makeString;
import java.math.BigInteger;

final class IntToStringProc
    extends Procedure
{
    @Override
    Object doApply(Evaluator eval, Object[] args)
        throws FusionException
    {
        checkArityExact(1, args);

        BigInteger val = checkNullableIntArg(eval, this, 0, args);
        String text = (val == null ? null : val.toString());

        return makeString(eval, text);
    }
}
