/*
Copyright (C) 2017 Bengt Martensson.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 3 of the License, or (at
your option) any later version.

This program is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License along with
this program. If not, see http://www.gnu.org/licenses/.
*/

package org.harctoolbox.irp;

import java.util.Map;
import org.harctoolbox.ircore.ThisCannotHappenException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public final class InfiniteBitField extends BitField {

    public InfiniteBitField(String str) {
        this(new ParserDriver(str));
    }

    public InfiniteBitField(ParserDriver parserDriver) {
        this((IrpParser.Infinite_bitfieldContext) parserDriver.getParser().bitfield());
    }

    public InfiniteBitField(IrpParser.Infinite_bitfieldContext ctx) {
        super(ctx);
        complement = ! (ctx.getChild(0) instanceof IrpParser.Primary_itemContext);
        data = PrimaryItem.newPrimaryItem(ctx.primary_item(0));
        chop = PrimaryItem.newPrimaryItem(ctx.primary_item(1));
    }

    private InfiniteBitField(PrimaryItem data, PrimaryItem chop, boolean complement) {
        super(null);
        this.data = data;
        this.chop = chop;
        this.complement = complement;
    }

    @Override
    public InfiniteBitField substituteConstantVariables(Map<String, Long> constantVariables) {
        return new InfiniteBitField(data.substituteConstantVariables(constantVariables),
                chop.substituteConstantVariables(constantVariables), complement);
    }

    @Override
    public long toLong(NameEngine nameEngine) throws NameUnassignedException {
        long x = data.toLong(nameEngine) >>> chop.toLong(nameEngine);
        if (complement)
            x = ~x;

        return x;
    }

    @Override
    public long getWidth(NameEngine nameEngine) {
        return MAXWIDTH;
    }

    @Override
    public String toString(NameEngine nameEngine) {
        String chopString;
        try {
            chopString = Long.toString(chop.toLong(nameEngine));
        } catch (NameUnassignedException ex) {
            chopString = chop.toIrpString(10);
        }

        String dataString;
        try {
            dataString = Long.toString(data.toLong(nameEngine));
        } catch (NameUnassignedException ex) {
            dataString = data.toIrpString(10);
        }

        return (complement ? "~" : "") + dataString + "::" + chopString;
    }

    @Override
    public String toIrpString(int radix) {
        return (complement ? "~" : "") + data.toIrpString(radix) + "::" + chop.toIrpString(10);
    }

    @Override
    public Element toElement(Document document) {
        Element element = super.toElement(document);
        element.setAttribute("complement", Boolean.toString(complement));
        Element dataElement = document.createElement("Data");
        dataElement.appendChild(data.toElement(document));
        element.appendChild(dataElement);
        try {
            if (!(chop instanceof Number && chop.toLong() == 0)) {
                Element chopElement = document.createElement("Chop");
                chopElement.appendChild(chop.toElement(document));
                element.appendChild(chopElement);
            }
        } catch (NameUnassignedException ex) {
            throw new ThisCannotHappenException();
        }
        return element;
    }

    @Override
    public Map<String, Object> propertiesMap(boolean eval, GeneralSpec generalSpec, NameEngine nameEngine) {
        //Map<String, Object> map = propertiesMap(IrSignal.Pass.intro, IrSignal.Pass.intro, generalSpec, nameEngine);
        Map<String, Object> map = super.propertiesMap(eval, generalSpec, nameEngine);
        map.put("kind", "InfiniteBitFieldExpression");
        return map;
    }

    @Override
    public Integer numberOfBits() {
        return 0;
    }
}
