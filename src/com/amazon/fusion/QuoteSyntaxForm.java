// Copyright (c) 2012-2018 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

final class QuoteSyntaxForm
    extends SyntacticForm
{
    @Override
    SyntaxValue expand(Expander expander, Environment env, SyntaxSexp stx)
        throws FusionException
    {
        check(expander, stx).arityExact(2);

        return stx;
    }


    @Override
    CompiledForm compile(Compiler comp, Environment env, SyntaxSexp stx)
        throws FusionException
    {
        SyntaxValue quoted = stx.get(comp.getEvaluator(), 1);
        return new CompiledConstant(quoted);
    }
}
