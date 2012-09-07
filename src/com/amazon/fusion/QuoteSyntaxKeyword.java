// Copyright (c) 2012 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

final class QuoteSyntaxKeyword
    extends KeywordValue
{
    QuoteSyntaxKeyword()
    {
        super("DATUM",
              "Returns a syntax object retaining the lexical information of DATUM.");
    }


    @Override
    SyntaxValue prepare(Evaluator eval, Environment env, SyntaxSexp source)
        throws SyntaxFailure
    {
        check(source).arityExact(2);

        return source;
    }


    @Override
    CompiledForm compile(Evaluator eval, Environment env, SyntaxSexp source)
        throws FusionException
    {
        SyntaxValue quoted = source.get(1);
        return new CompiledQuoteSyntax(quoted);
    }


    //========================================================================


    private static final class CompiledQuoteSyntax
        implements CompiledForm
    {
        private final SyntaxValue myQuoted;

        CompiledQuoteSyntax(SyntaxValue quoted)
        {
            myQuoted = quoted;
        }

        @Override
        public Object doExec(Evaluator eval, Store store)
            throws FusionException
        {
            return myQuoted;
        }
    }
}
