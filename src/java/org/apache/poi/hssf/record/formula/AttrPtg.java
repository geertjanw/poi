
/* ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2002 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Apache" and "Apache Software Foundation" and
 *    "Apache POI" must not be used to endorse or promote products
 *    derived from this software without prior written permission. For
 *    written permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    "Apache POI", nor may "Apache" appear in their name, without
 *    prior written permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

/*
 * AttrPtg.java
 *
 * Created on November 21, 2001, 1:20 PM
 */
package org.apache.poi.hssf.record.formula;

import org.apache.poi.hssf.util.SheetReferences;

import org.apache.poi.util.LittleEndian;
import org.apache.poi.util.BitField;

import java.util.List;

/**
 * "Special Attributes"
 * This seems to be a Misc Stuff and Junk record.  One function it serves is
 * in SUM functions (i.e. SUM(A1:A3) causes an area PTG then an ATTR with the SUM option set)
 * @author  andy
 * @author Jason Height (jheight at chariot dot net dot au)
 */

public class AttrPtg
    extends OperationPtg
{
    public final static byte sid  = 0x19;
    private final static int  SIZE = 4;
    private byte              field_1_options;
    private short             field_2_data;
    private BitField          semiVolatile = new BitField(0x01);
    private BitField          optiIf       = new BitField(0x02);
    private BitField          optiChoose   = new BitField(0x04);
    private BitField          optGoto      = new BitField(0x08);
    private BitField          sum          = new BitField(0x10);
    private BitField          baxcel       = new BitField(0x20);
    private BitField          space        = new BitField(0x40);

    public AttrPtg() {
    }
    
    public AttrPtg(byte [] data, int offset)
    {
        offset++;   // adjust past id
        field_1_options = data[ offset + 0 ];
        field_2_data    = LittleEndian.getShort(data, offset + 1);
    }

    public void setOptions(byte options)
    {
        field_1_options = options;
    }

    public byte getOptions()
    {
        return field_1_options;
    }

    public boolean isSemiVolatile()
    {
        return semiVolatile.isSet(getOptions());
    }

    public boolean isOptimizedIf()
    {
        return optiIf.isSet(getOptions());
    }

    public boolean isOptimizedChoose()
    {
        return optiChoose.isSet(getOptions());
    }

    // lets hope no one uses this anymore
    public boolean isGoto()
    {
        return optGoto.isSet(getOptions());
    }

    public boolean isSum()
    {
        return sum.isSet(getOptions());
    }
    
    public void setSum(boolean bsum) {
        field_1_options=sum.setByteBoolean(field_1_options,bsum);
    }

    public void setOptimizedIf(boolean bif) {
        field_1_options=optiIf.setByteBoolean(field_1_options,bif);
    }

    // lets hope no one uses this anymore
    public boolean isBaxcel()
    {
        return baxcel.isSet(getOptions());
    }

    // biff3&4 only  shouldn't happen anymore
    public boolean isSpace()
    {
        return space.isSet(getOptions());
    }

    public void setData(short data)
    {
        field_2_data = data;
    }

    public short getData()
    {
        return field_2_data;
    }

    public String toString()
    {
        StringBuffer buffer = new StringBuffer();

        buffer.append("AttrPtg\n");
        buffer.append("options=").append(field_1_options).append("\n");
        buffer.append("data   =").append(field_2_data).append("\n");
        buffer.append("semi   =").append(isSemiVolatile()).append("\n");
        buffer.append("optimif=").append(isOptimizedIf()).append("\n");
        buffer.append("optchos=").append(isOptimizedChoose()).append("\n");
        buffer.append("isGoto =").append(isGoto()).append("\n");
        buffer.append("isSum  =").append(isSum()).append("\n");
        buffer.append("isBaxce=").append(isBaxcel()).append("\n");
        buffer.append("isSpace=").append(isSpace()).append("\n");
        return buffer.toString();
    }

    public void writeBytes(byte [] array, int offset)
    {
        array[offset]=sid;
        array[offset+1]=field_1_options;
        LittleEndian.putShort(array,offset+2,field_2_data);                
    }

    public int getSize()
    {
        return SIZE;
    }

    public String toFormulaString(String[] operands) {
        if(space.isSet(field_1_options)) {
            return operands[ 0 ];
        } else if (optiIf.isSet(field_1_options)) {
            return toFormulaString((SheetReferences)null) + "(" + operands[ 0 ]             +")"; 
        } else if (optGoto.isSet(field_1_options)) {
            return toFormulaString((SheetReferences)null) + operands[0];   //goto isn't a real formula element should not show up
        } else {
            return toFormulaString((SheetReferences)null) + "(" + operands[ 0 ] + ")";
        }
    }
  

    public int getNumberOfOperands()
    {
        return 1;
    }

    public int getType()
    {
        return -1;
    }
        
   public String toFormulaString(SheetReferences refs) {
      if(semiVolatile.isSet(field_1_options)) {
        return "ATTR(semiVolatile)";
      }
      if(optiIf.isSet(field_1_options)) {
        return "IF";
      }
      if( optiChoose.isSet(field_1_options)) {
        return "CHOOSE";
      }
      if(optGoto.isSet(field_1_options)) {
        return "";
      }
      if(sum.isSet(field_1_options)) {
        return "SUM";
      }
      if(baxcel.isSet(field_1_options)) {
        return "ATTR(baxcel)";
      }
      if(space.isSet(field_1_options)) {
        return "";
      }
      return "UNKNOWN ATTRIBUTE";
     }
    
    
 
    public byte getDefaultOperandClass() {return Ptg.CLASS_VALUE;}

    public Object clone() {
      AttrPtg ptg = new AttrPtg();
      ptg.field_1_options = field_1_options;
      ptg.field_2_data = field_2_data;
      return ptg;
    }
}
