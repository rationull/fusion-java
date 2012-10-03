// Copyright (c) 2012 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.FusionVector.isVector;
import static com.amazon.fusion.FusionVector.unsafeVectorCopy;
import static com.amazon.fusion.FusionVector.unsafeVectorSize;
import com.amazon.ion.IonSequence;
import com.amazon.ion.IonValue;

final class ApplyProc
    extends Procedure
{
    ApplyProc()
    {
        //    "                                                                               |
        super("Calls the given PROC with arguments that are the (optional) ARGs prepended to\n" +
              "the elements of SEQUENCE. The PROC is called in tail position.\n" +
              "(apply + [1, 2])         => 3\n" +
              "(apply + 10 11 [1, 2])   => 24",
              "proc", "arg", DOTDOTDOT, "sequence");
    }

    @Override
    Object doApply(Evaluator eval, Object[] args)
        throws FusionException
    {
        checkArityAtLeast(2, args);

        int arity = args.length;

        Procedure proc = checkProcArg(0, args);

        Object rest = args[arity - 1];
        boolean restIsVector = isVector(eval, rest);

        int restLen;
        if (restIsVector)
        {
            restLen = unsafeVectorSize(eval, rest);
        }
        else if (rest instanceof IonSequence)
        {
            restLen = ((IonSequence) rest).size();
        }
        else
        {
            throw argFailure("list or sexp", arity - 1, args);
        }

        int procArity = restLen + arity - 2;
        Object[] procArgs = new Object[procArity];

        int arg = 0;
        for (int i = 1; i < arity - 1; i++)
        {
            procArgs[arg++] = args[i];
        }

        if (restIsVector)
        {
            unsafeVectorCopy(eval, rest, 0, procArgs, arg, restLen);
        }
        else
        {
            for (IonValue dom : (IonSequence) rest)
            {
                procArgs[arg++] = eval.inject(dom);
            }
            assert arg == procArity;
        }

        return eval.bounceTailCall(proc, procArgs);
    }
}
