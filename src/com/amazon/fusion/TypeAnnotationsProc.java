// Copyright (c) 2012-2013 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.FusionList.immutableList;
import static com.amazon.fusion.FusionUtils.EMPTY_OBJECT_ARRAY;
import static com.amazon.fusion.FusionUtils.EMPTY_STRING_ARRAY;
import com.amazon.ion.IonValue;

final class TypeAnnotationsProc
    extends Procedure1
{
    TypeAnnotationsProc()
    {
        //    "                                                                               |
        super("Returns a non-null immutable list of symbols containing the user type\n" +
              "annotations on the `value`.",
              "value");
    }

    @Override
    Object doApply(Evaluator eval, Object arg)
        throws FusionException
    {
        String[] anns = EMPTY_STRING_ARRAY;

        if (arg instanceof Annotated)
        {
            anns = ((Annotated) arg).annotationsAsJavaStrings();
        }
        else
        {
            IonValue value = castToIonValueMaybe(arg);

            if (value != null)
            {
                anns = value.getTypeAnnotations();
            }
        }

        Object[] result = EMPTY_OBJECT_ARRAY;
        int length = anns.length;
        if (length != 0)
        {
            result = new Object[length];
            for (int i = 0; i < length; i++)
            {
                result[i] = eval.newSymbol(anns[i]);
            }
        }

        // Returning immutable list allows us to return a shared structure
        // when possible, avoiding copies.
        return immutableList(eval, result);
    }
}
