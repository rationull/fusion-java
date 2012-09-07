// Copyright (c) 2012 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

/**
 * The results of the syntax preparation phase, ready for execution.
 */
interface CompiledForm
{
    Object doExec(Evaluator eval, Store store)
        throws FusionException;
}
