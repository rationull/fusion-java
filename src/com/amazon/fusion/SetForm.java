// Copyright (c) 2012-2017 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

final class SetForm
    extends SyntacticForm
{
    @Override
    SyntaxValue expand(Expander expander, Environment env, SyntaxSexp stx)
        throws FusionException
    {
        final Evaluator eval = expander.getEvaluator();

        SyntaxChecker check = check(eval, stx);
        check.arityExact(3);

        SyntaxSymbol id = check.requiredIdentifier("variable identifier", 1);
        Binding binding = id.resolve();

        String message = binding.mutationSyntaxErrorMessage();
        if (message != null)
        {
            throw check.failure(message + ": " + id);
        }

        SyntaxValue[] children = stx.extract(eval);
        SyntaxValue valueExpr = stx.get(eval, 2);
        children[2] = expander.expandExpression(env, valueExpr);

        return stx.copyReplacingChildren(eval, children);
    }


    @Override
    CompiledForm compile(Compiler comp, Environment env, SyntaxSexp stx)
        throws FusionException
    {
        return comp.compileSet(env, stx);
    }
}
