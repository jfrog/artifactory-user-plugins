/*
 * $HeadURL: https://svn.apache.org/repos/asf/jakarta/httpcomponents/oac.hc3x/tags/HTTPCLIENT_3_1/src/java/org/apache/commons/httpclient/URI.java $
 * $Revision: 564973 $
 * $Date: 2007-08-11 22:51:47 +0200 (Sat, 11 Aug 2007) $
 *
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 * Based on: /commons-httpclient/commons-httpclient3.1/commons-httpclient-3.1-sources.jar!/org/apache/commons/httpclient/URI.java
 */

package com.jfrog.maven.plugins.pomwithdeps.utils;

import java.util.BitSet;

/**
 * The interface for the URI(Uniform Resource Identifiers) version of RFC 2396.
 * This class has the purpose of supportting of parsing a URI reference to
 * extend any specific protocols, the character encoding of the protocol to
 * be transported and the charset of the document.
 * <p/>
 * A URI is always in an "escaped" form, since escaping or unescaping a
 * completed URI might change its semantics.
 * <p/>
 * Implementers should be careful not to escape or unescape the same string
 * more than once, since unescaping an already unescaped string might lead to
 * misinterpreting a percent data character as another escaped character,
 * or vice versa in the case of escaping an already escaped string.
 * <p/>
 * In order to avoid these problems, data types used as follows:
 * <p><blockquote><pre>
 *   URI character sequence: char
 *   octet sequence: byte
 *   original character sequence: String
 * </pre></blockquote><p>
 * <p/>
 * So, a URI is a sequence of characters as an array of a char type, which
 * is not always represented as a sequence of octets as an array of byte.
 * <p/>
 * <p/>
 * URI Syntactic Components
 * <p><blockquote><pre>
 * - In general, written as follows:
 *   Absolute URI = &lt;scheme&gt:&lt;scheme-specific-part&gt;
 *   Generic URI = &lt;scheme&gt;://&lt;authority&gt;&lt;path&gt;?&lt;query&gt;
 * <p/>
 * - Syntax
 *   absoluteURI   = scheme ":" ( hier_part | opaque_part )
 *   hier_part     = ( net_path | abs_path ) [ "?" query ]
 *   net_path      = "//" authority [ abs_path ]
 *   abs_path      = "/"  path_segments
 * </pre></blockquote><p>
 * <p/>
 * The following examples illustrate URI that are in common use.
 * <pre>
 * ftp://ftp.is.co.za/rfc/rfc1808.txt
 *    -- ftp scheme for File Transfer Protocol services
 * gopher://spinaltap.micro.umn.edu/00/Weather/California/Los%20Angeles
 *    -- gopher scheme for Gopher and Gopher+ Protocol services
 * http://www.math.uio.no/faq/compression-faq/part1.html
 *    -- http scheme for Hypertext Transfer Protocol services
 * mailto:mduerst@ifi.unizh.ch
 *    -- mailto scheme for electronic mail addresses
 * news:comp.infosystems.www.servers.unix
 *    -- news scheme for USENET news groups and articles
 * telnet://melvyl.ucop.edu/
 *    -- telnet scheme for interactive services via the TELNET Protocol
 * </pre>
 * Please, notice that there are many modifications from URL(RFC 1738) and
 * relative URL(RFC 1808).
 * <p/>
 * <b>The expressions for a URI</b>
 * <p><pre>
 * For escaped URI forms
 *  - URI(char[]) // constructor
 *  - char[] getRawXxx() // method
 *  - String getEscapedXxx() // method
 *  - String toString() // method
 * <p/>
 * For unescaped URI forms
 *  - URI(String) // constructor
 *  - String getXXX() // method
 * </pre><p>
 *
 * @author <a href="mailto:jericho@apache.org">Sung-Gu</a>
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * @version $Revision: 564973 $ $Date: 2002/03/14 15:14:01
 */
public class URI {
    /**
     * The percent "%" character always has the reserved purpose of being the
     * escape indicator, it must be escaped as "%25" in order to be used as
     * data within a URI.
     */
    protected static final BitSet percent = new BitSet(256);

    // Static initializer for percent
    static {
        percent.set('%');
    }


    /**
     * BitSet for digit.
     * <p><blockquote><pre>
     * digit    = "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" |
     *            "8" | "9"
     * </pre></blockquote><p>
     */
    protected static final BitSet digit = new BitSet(256);

    // Static initializer for digit
    static {
        for (int i = '0'; i <= '9'; i++) {
            digit.set(i);
        }
    }


    /**
     * BitSet for alpha.
     * <p><blockquote><pre>
     * alpha         = lowalpha | upalpha
     * </pre></blockquote><p>
     */
    protected static final BitSet alpha = new BitSet(256);

    // Static initializer for alpha
    static {
        for (int i = 'a'; i <= 'z'; i++) {
            alpha.set(i);
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            alpha.set(i);
        }
    }


    /**
     * BitSet for alphanum (join of alpha &amp; digit).
     * <p><blockquote><pre>
     *  alphanum      = alpha | digit
     * </pre></blockquote><p>
     */
    protected static final BitSet alphanum = new BitSet(256);

    // Static initializer for alphanum
    static {
        alphanum.or(alpha);
        alphanum.or(digit);
    }

    /**
     * BitSet for hex.
     * <p><blockquote><pre>
     * hex           = digit | "A" | "B" | "C" | "D" | "E" | "F" |
     *                         "a" | "b" | "c" | "d" | "e" | "f"
     * </pre></blockquote><p>
     */
    protected static final BitSet hex = new BitSet(256);

    // Static initializer for hex
    static {
        hex.or(digit);
        for (int i = 'a'; i <= 'f'; i++) {
            hex.set(i);
        }
        for (int i = 'A'; i <= 'F'; i++) {
            hex.set(i);
        }
    }


    /**
     * BitSet for escaped.
     * <p><blockquote><pre>
     * escaped       = "%" hex hex
     * </pre></blockquote><p>
     */
    protected static final BitSet escaped = new BitSet(256);

    // Static initializer for escaped
    static {
        escaped.or(percent);
        escaped.or(hex);
    }

    /**
     * BitSet for mark.
     * <p><blockquote><pre>
     * mark          = "-" | "_" | "." | "!" | "~" | "*" | "'" |
     *                 "(" | ")"
     * </pre></blockquote><p>
     */
    protected static final BitSet mark = new BitSet(256);

    // Static initializer for mark
    static {
        mark.set('-');
        mark.set('_');
        mark.set('.');
        mark.set('!');
        mark.set('~');
        mark.set('*');
        mark.set('\'');
        mark.set('(');
        mark.set(')');
    }

    /**
     * Data characters that are allowed in a URI but do not have a reserved
     * purpose are called unreserved.
     * <p><blockquote><pre>
     * unreserved    = alphanum | mark
     * </pre></blockquote><p>
     */
    protected static final BitSet unreserved = new BitSet(256);

    // Static initializer for unreserved
    static {
        unreserved.or(alphanum);
        unreserved.or(mark);
    }

    /**
     * BitSet for reserved.
     * <p><blockquote><pre>
     * reserved      = ";" | "/" | "?" | ":" | "@" | "&amp;" | "=" | "+" |
     *                 "$" | ","
     * </pre></blockquote><p>
     */
    protected static final BitSet reserved = new BitSet(256);

    // Static initializer for reserved
    static {
        reserved.set(';');
        reserved.set('/');
        reserved.set('?');
        reserved.set(':');
        reserved.set('@');
        reserved.set('&');
        reserved.set('=');
        reserved.set('+');
        reserved.set('$');
        reserved.set(',');
    }

    /**
     * BitSet for uric.
     * <p><blockquote><pre>
     * uric          = reserved | unreserved | escaped
     * </pre></blockquote><p>
     */
    protected static final BitSet uric = new BitSet(256);

    // Static initializer for uric
    static {
        uric.or(reserved);
        uric.or(unreserved);
        uric.or(escaped);
    }

    /**
     * Those characters that are allowed for the query component.
     */
    public static final BitSet allowed_query = new BitSet(256);

    // Static initializer for allowed_query
    static {
        allowed_query.or(uric);
        allowed_query.clear('%');
    }
}