<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
                xmlns:exsl="http://exslt.org/common"
                extension-element-prefixes="exsl">

<!-- Used to generate the Java classes in this package.
     Changes to these classes should be effected by modifying this stylesheet then re-running it,
     using a stylesheet processor that understands the exsl directives such as xsltproc -->

<xsl:template match="/">
    <xsl:variable name="license">/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
</xsl:variable>

    <xsl:for-each select="descendant-or-self::node()[name()='type']">
        <xsl:variable name="classname"><xsl:call-template name="dashToCamel"><xsl:with-param name="input" select="@name"/></xsl:call-template>Matcher</xsl:variable>

        <xsl:if test="@provides = 'frame' or @provides = 'sasl-frame'">
          <xsl:variable name="frameSuperclass">
            <xsl:choose>
                <xsl:when test="@name = 'transfer'">FrameWithPayloadMatchingHandler</xsl:when>
                <xsl:otherwise>FrameWithNoPayloadMatchingHandler</xsl:otherwise>
            </xsl:choose>
          </xsl:variable>
          <xsl:call-template name="frameClass">
              <xsl:with-param name="license" select="$license"/>
              <xsl:with-param name="classname" select="$classname"/>
              <xsl:with-param name="superclass" select="$frameSuperclass"/>
          </xsl:call-template>
        </xsl:if>

        <xsl:if test="@provides = 'source' or @provides = 'target'">
          <xsl:variable name="typename"><xsl:call-template name="dashToCamel"><xsl:with-param name="input" select="@name"/></xsl:call-template></xsl:variable>
          <xsl:call-template name="sourceOrTargetClass">
              <xsl:with-param name="license" select="$license"/>
              <xsl:with-param name="classname" select="$classname"/>
              <xsl:with-param name="typename" select="$typename"/>
          </xsl:call-template>
        </xsl:if>
    </xsl:for-each>
</xsl:template>


<!-- *************************************************************************************************************** -->

<xsl:template name="frameClass">
    <xsl:param name="license"/>
    <xsl:param name="classname"/>
    <xsl:param name="superclass"/>
  <exsl:document href="{$classname}.java" method="text">
  <xsl:value-of select="$license"/>
package org.apache.qpid.jms.test.testpeer.matchers;

import java.util.HashMap;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.UnsignedLong;
import org.apache.qpid.jms.test.testpeer.FrameType;
import org.apache.qpid.jms.test.testpeer.<xsl:value-of select="$superclass"/>;
import org.hamcrest.Matcher;

/**
 * Generated by generate-matchers.xsl, which resides in this package.
 */
public class <xsl:value-of select="$classname"/> extends <xsl:value-of select="$superclass"/>
{
    /** Note that the ordinals of the Field enums match the order specified in the AMQP spec */
    public enum Field
    {
<xsl:for-each select="descendant::node()[name()='field']">
<xsl:text>        </xsl:text><xsl:call-template name="toUpperDashToUnderscore"><xsl:with-param name="input" select="@name"/></xsl:call-template>,
</xsl:for-each>    }

    public <xsl:value-of select="$classname"/>()
    {
        super(FrameType.<xsl:choose><xsl:when test="@provides='sasl-frame'">SASL</xsl:when><xsl:otherwise>AMQP</xsl:otherwise></xsl:choose>,
              ANY_CHANNEL,
              UnsignedLong.valueOf(<xsl:value-of select="concat(substring(descendant::node()[name()='descriptor']/@code,1,10),substring(descendant::node()[name()='descriptor']/@code,14))"/>L),
              Symbol.valueOf("<xsl:value-of select="descendant::node()[name()='descriptor']/@name"/>"),
              new HashMap&lt;Enum&lt;?&gt;, Matcher&lt;?&gt;&gt;(),
              null);
    }

    @Override
    public <xsl:value-of select="$classname"/> onSuccess(Runnable onSuccessAction)
    {
        super.onSuccess(onSuccessAction);
        return this;
    }
<xsl:for-each select="descendant::node()[name()='field']">
    public <xsl:value-of select="$classname"/> with<xsl:call-template name="dashToCamel"><xsl:with-param name="input" select="@name"/></xsl:call-template>(Matcher&lt;?&gt; m)
    {
        getMatchers().put(Field.<xsl:call-template name="toUpperDashToUnderscore"><xsl:with-param name="input" select="@name"/></xsl:call-template>, m);
        return this;
    }
</xsl:for-each>
<xsl:for-each select="descendant::node()[name()='field']">
    public Object getReceived<xsl:call-template name="dashToCamel"><xsl:with-param name="input" select="@name"/></xsl:call-template>()
    {
        return getReceivedFields().get(Field.<xsl:call-template name="toUpperDashToUnderscore"><xsl:with-param name="input" select="@name"/></xsl:call-template>);
    }
</xsl:for-each>
    @Override
    protected Enum&lt;?&gt; getField(int fieldIndex)
    {
        return Field.values()[fieldIndex];
    }
}

</exsl:document>

</xsl:template>

<!-- *************************************************************************************************************** -->

<xsl:template name="sourceOrTargetClass">
    <xsl:param name="license"/>
    <xsl:param name="classname"/>
    <xsl:param name="typename"/>
  <exsl:document href="{$classname}.java" method="text">
  <xsl:value-of select="$license"/>
package org.apache.qpid.jms.test.testpeer.matchers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

import java.util.HashMap;
import java.util.List;
import org.apache.qpid.proton.amqp.DescribedType;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.UnsignedLong;
import org.apache.qpid.jms.test.testpeer.AbstractFieldAndDescriptorMatcher;
import org.hamcrest.Matcher;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

/**
 * Generated by generate-matchers.xsl, which resides in this package.
 */
public class <xsl:value-of select="$classname"/> extends TypeSafeMatcher&lt;Object&gt;
{
    private <xsl:value-of select="$classname"/>Core coreMatcher = new <xsl:value-of select="$classname"/>Core();
    private String mismatchTextAddition;
    private Object described;
    private Object descriptor;

    public <xsl:value-of select="$classname"/>()
    {
    }

    @Override
    protected boolean matchesSafely(Object received)
    {
        try
        {
            assertThat(received, instanceOf(DescribedType.class));
            descriptor = ((DescribedType)received).getDescriptor();
            if(!coreMatcher.descriptorMatches(descriptor))
            {
                mismatchTextAddition = "Descriptor mismatch";
                return false;
            }

            described = ((DescribedType)received).getDescribed();
            assertThat(described, instanceOf(List.class));
            @SuppressWarnings("unchecked")
            List&lt;Object&gt; fields = (List&lt;Object&gt;) described;

            coreMatcher.verifyFields(fields);
        }
        catch (AssertionError ae)
        {
            mismatchTextAddition = "AssertionFailure: " + ae.getMessage();
            return false;
        }

        return true;
    }

    @Override
    protected void describeMismatchSafely(Object item, Description mismatchDescription)
    {
        mismatchDescription.appendText("\nActual form: ").appendValue(item);

        mismatchDescription.appendText("\nExpected descriptor: ")
                .appendValue(coreMatcher.getSymbolicDescriptor())
                .appendText(" / ")
                .appendValue(coreMatcher.getNumericDescriptor());

        if(mismatchTextAddition != null)
        {
            mismatchDescription.appendText("\nAdditional info: ").appendValue(mismatchTextAddition);
        }
    }

    public void describeTo(Description description)
    {
        description
            .appendText("<xsl:value-of select="$typename"/> which matches: ")
            .appendValue(coreMatcher.getMatchers());
    }

<xsl:for-each select="descendant::node()[name()='field']">
    public <xsl:value-of select="$classname"/> with<xsl:call-template name="dashToCamel"><xsl:with-param name="input" select="@name"/></xsl:call-template>(Matcher&lt;?&gt; m)
    {
        coreMatcher.with<xsl:call-template name="dashToCamel"><xsl:with-param name="input" select="@name"/></xsl:call-template>(m);
        return this;
    }
</xsl:for-each>
<xsl:for-each select="descendant::node()[name()='field']">
    public Object getReceived<xsl:call-template name="dashToCamel"><xsl:with-param name="input" select="@name"/></xsl:call-template>()
    {
        return coreMatcher.getReceived<xsl:call-template name="dashToCamel"><xsl:with-param name="input" select="@name"/></xsl:call-template>();
    }
</xsl:for-each>


    //Inner core matching class
    public static class <xsl:value-of select="$classname"/>Core extends AbstractFieldAndDescriptorMatcher
    {
        /** Note that the ordinals of the Field enums match the order specified in the AMQP spec */
        public enum Field
        {
    <xsl:for-each select="descendant::node()[name()='field']">
    <xsl:text>        </xsl:text><xsl:call-template name="toUpperDashToUnderscore"><xsl:with-param name="input" select="@name"/></xsl:call-template>,
    </xsl:for-each>    }

        public <xsl:value-of select="$classname"/>Core()
        {
            super(UnsignedLong.valueOf(<xsl:value-of select="concat(substring(descendant::node()[name()='descriptor']/@code,1,10),substring(descendant::node()[name()='descriptor']/@code,14))"/>L),
                  Symbol.valueOf("<xsl:value-of select="descendant::node()[name()='descriptor']/@name"/>"),
                  new HashMap&lt;Enum&lt;?&gt;, Matcher&lt;?&gt;&gt;());
        }

<xsl:for-each select="descendant::node()[name()='field']">
        public <xsl:value-of select="$classname"/>Core with<xsl:call-template name="dashToCamel"><xsl:with-param name="input" select="@name"/></xsl:call-template>(Matcher&lt;?&gt; m)
        {
            getMatchers().put(Field.<xsl:call-template name="toUpperDashToUnderscore"><xsl:with-param name="input" select="@name"/></xsl:call-template>, m);
            return this;
        }
</xsl:for-each>
<xsl:for-each select="descendant::node()[name()='field']">
        public Object getReceived<xsl:call-template name="dashToCamel"><xsl:with-param name="input" select="@name"/></xsl:call-template>()
        {
            return getReceivedFields().get(Field.<xsl:call-template name="toUpperDashToUnderscore"><xsl:with-param name="input" select="@name"/></xsl:call-template>);
        }
</xsl:for-each>
        @Override
        protected Enum&lt;?&gt; getField(int fieldIndex)
        {
            return Field.values()[fieldIndex];
        }
    }
}

</exsl:document>

</xsl:template>

<!-- *************************************************************************************************************** -->

<xsl:template name="constructFromLiteral">
    <xsl:param name="type"/>
    <xsl:param name="value"/>
    <xsl:choose>
        <xsl:when test="$type = 'string'">"<xsl:value-of select="$value"/></xsl:when>
        <xsl:when test="$type = 'symbol'">Symbol.valueOf("<xsl:value-of select="$value"/>")</xsl:when>
        <xsl:when test="$type = 'ubyte'">UnsignedByte.valueOf((byte) <xsl:value-of select="$value"/>)</xsl:when>
        <xsl:when test="$type = 'ushort'">UnsignedShort.valueOf((short) <xsl:value-of select="$value"/>)</xsl:when>
        <xsl:when test="$type = 'uint'">UnsignedInteger.valueOf(<xsl:value-of select="$value"/>)</xsl:when>
        <xsl:when test="$type = 'ulong'">UnsignedLong.valueOf(<xsl:value-of select="$value"/>L)</xsl:when>
        <xsl:when test="$type = 'long'"><xsl:value-of select="$value"/>L</xsl:when>
        <xsl:when test="$type = 'short'">(short)<xsl:value-of select="$value"/></xsl:when>
        <xsl:when test="$type = 'short'">(byte)<xsl:value-of select="$value"/></xsl:when>
        <xsl:otherwise><xsl:value-of select="$value"/></xsl:otherwise>
    </xsl:choose>
</xsl:template>

<!-- *************************************************************************************************************** -->
<xsl:template name="substringAfterLast"><xsl:param name="input"/><xsl:param name="arg"/>
        <xsl:choose>
            <xsl:when test="contains($input,$arg)"><xsl:call-template name="substringAfterLast"><xsl:with-param name="input"><xsl:value-of select="substring-after($input,$arg)"/></xsl:with-param><xsl:with-param name="arg"><xsl:value-of select="$arg"/></xsl:with-param></xsl:call-template></xsl:when>
            <xsl:otherwise><xsl:value-of select="$input"/></xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="initCap"><xsl:param name="input"/><xsl:value-of select="translate(substring($input,1,1),'abcdefghijklmnopqrstuvwxyz','ABCDEFGHIJKLMNOPQRSTUVWXYZ')"/><xsl:value-of select="substring($input,2)"/></xsl:template>

    <xsl:template name="initLower"><xsl:param name="input"/><xsl:value-of select="translate(substring($input,1,1),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')"/><xsl:value-of select="substring($input,2)"/></xsl:template>

    <xsl:template name="toUpper"><xsl:param name="input"/><xsl:value-of select="translate($input,'abcdefghijklmnopqrstuvwxyz','ABCDEFGHIJKLMNOPQRSTUVWXYZ')"/></xsl:template>

    <xsl:template name="toUpperDashToUnderscore"><xsl:param name="input"/><xsl:value-of select="translate($input,'abcdefghijklmnopqrstuvwxyz-','ABCDEFGHIJKLMNOPQRSTUVWXYZ_')"/></xsl:template>

    <xsl:template name="dashToCamel">
        <xsl:param name="input"/>
        <xsl:choose>
            <xsl:when test="contains($input,'-')"><xsl:call-template name="initCap"><xsl:with-param name="input" select="substring-before($input,'-')"/></xsl:call-template><xsl:call-template name="dashToCamel"><xsl:with-param name="input" select="substring-after($input,'-')"/></xsl:call-template></xsl:when>
            <xsl:otherwise><xsl:call-template name="initCap"><xsl:with-param name="input" select="$input"/></xsl:call-template></xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="dashToLowerCamel">
        <xsl:param name="input"/>
        <xsl:call-template name="initLower"><xsl:with-param name="input"><xsl:call-template name="dashToCamel"><xsl:with-param name="input" select="$input"/></xsl:call-template></xsl:with-param></xsl:call-template>
    </xsl:template>
</xsl:stylesheet>
