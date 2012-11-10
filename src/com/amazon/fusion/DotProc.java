// Copyright (c) 2012 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.FusionUtils.writeFriendlyIndex;
import static com.amazon.fusion.FusionVector.isVector;
import static com.amazon.fusion.FusionVector.unsafeVectorRef;
import static com.amazon.fusion.FusionVector.unsafeVectorSize;
import static com.amazon.fusion.FusionVoid.voidValue;
import com.amazon.ion.IonContainer;
import com.amazon.ion.IonSequence;
import com.amazon.ion.IonStruct;

final class DotProc
    extends Procedure
{
    DotProc()
    {
        //    "                                                                               |
        super("Traverses down through an Ion data structure.\n" +
              "CONTAINER must be Ion container (struct, list, or sexp).\n" +
              "Each PART must be a string, symbol, or int, to denote either a struct's\n" +
              "field-name or a sequence's index.",
              "container", "part", DOTDOTDOT);
    }

    @SuppressWarnings("cast")
    @Override
    Object doApply(Evaluator eval, Object[] args)
        throws FusionException
    {
        checkArityAtLeast(1, args);

        boolean cIsVector = isVector(eval, args[0]);

        Object c;
        if (cIsVector)
        {
            c = args[0];
        }
        else
        {
            c = checkIonContainerArg(0, args);
        }
        Object value = c;

        final int lastArg = args.length - 1;
        for (int i = 1; i <= lastArg; i++)
        {
            if (cIsVector)
            {
                int index = checkIntArg(i, args);
                if (unsafeVectorSize(eval, c) <= index)
                {
                    return voidValue(eval);
                }
                value = unsafeVectorRef(eval, c, index);
            }
            else
            {
                IonContainer ic = (IonContainer) c;

                switch (ic.getType())
                {
                    case LIST:
                    case SEXP:
                    {
                        long index = checkLongArg(i, args);
                        if (ic.size() <= index)
                        {
                            return voidValue(eval);
                        }
                        IonSequence s = (IonSequence) ic;
                        value = s.get((int) index);
                        break;
                    }
                    case STRUCT:
                    {
                        String field = checkTextArg(i, args);
                        IonStruct s = (IonStruct) ic;
                        value = s.get(field);
                        break;
                    }
                    default:
                    {
                        throw new IllegalStateException();
                    }
                }
            }

            if (value == null) return voidValue(eval);

            if (i < lastArg)
            {
                cIsVector = isVector(eval, value);
                if (cIsVector)
                {
                    c = value;
                }
                else try
                {
                    c = (IonContainer) value;
                }
                catch (ClassCastException cce)
                {
                    StringBuilder out = new StringBuilder();
                    out.append("expected container before traversing ");
                    writeFriendlyIndex(out, i + 1);
                    out.append(" argument, had: ");
                    FusionValue.write(out, value);
                    out.append("\nArguments were:");
                    for (Object arg : args)
                    {
                        out.append("\n  ");
                        FusionValue.write(out, arg);
                    }
                    String message = out.toString();
                    throw contractFailure(message);
                }
            }
        }

        return value;
    }

}
