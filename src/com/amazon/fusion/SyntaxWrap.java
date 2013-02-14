// Copyright (c) 2012-2013 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import java.util.Iterator;
import java.util.Set;

/**
 * Linked-list of lexical information wrapped around a {@link SyntaxValue}
 * hierarchy.
 */
abstract class SyntaxWrap
{
    /**
     * @param returnMarks <em>returns</em> the marks from this wrap and those
     * deeper. Must be mutable and not null.
     *
     * @return not null
     */
    abstract Binding resolve(String identifier,
                             Iterator<SyntaxWrap> moreWraps,
                             Set<Integer> returnMarks);
}
