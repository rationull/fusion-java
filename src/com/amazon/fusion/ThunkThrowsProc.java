// Copyright (c) 2013 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;


final class ThunkThrowsProc
    extends Procedure1
{
    ThunkThrowsProc()
    {
        //    "                                                                               |
        super("XXX",
              "thunk");
    }

    @Override
    Object doApply(Evaluator eval, Object thunk)
        throws FusionException
    {
        // TODO type-check the thunk
        try
        {
            Procedure proc = (Procedure) thunk;
            eval.callNonTail(proc);
        }
        catch (SyntaxFailure e)
        {
            // Good!  We are expecting this.
            return eval.newString("syntax");
        }
        catch (ArgTypeFailure e)
        {
            return eval.newString("arg");
        }

        return eval.newBool(false);
    }
}
