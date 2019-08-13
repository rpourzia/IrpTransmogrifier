/*
Copyright (C) 2019 Bengt Martensson.

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.harctoolbox.analyze.Analyzer;
import org.harctoolbox.ircore.IrSequence;
import org.harctoolbox.ircore.ModulatedIrSequence;
import org.harctoolbox.ircore.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

public class ProtocolListDomFactory {

    public static Document protocolListToDom(Analyzer analyzer, List<Protocol> protocols, String[] names, int radix) {
        ProtocolListDomFactory factory = new ProtocolListDomFactory(analyzer, protocols, names, radix);
        return factory.getDocument();
    }

    private Map<Integer, Protocol> protocolsWithoutDefs;
    private final Document doc;
    private final String[] names;
    private final int radix;
    private final Analyzer analyzer;
    private final List<Protocol> protocols;
    private int counter;

    private ProtocolListDomFactory(Analyzer analyzer, List<Protocol> protocols, String[] names, int radix) {
//        if (protocols.size() != names.length || analyzer.getNoSequences() != names.length)
//            throw new IllegalArgumentException();

        this.protocolsWithoutDefs = new HashMap<>(8);
        this.analyzer = analyzer;
        this.protocols = protocols;
        this.names = names;
        this.radix = radix;
        this.counter = 0;

        doc = XmlUtils.newDocument(true);
        doc.appendChild(doc.createComment(XmlUtils.GIRR_COMMENT));
        Element remotes = doc.createElementNS(XmlUtils.GIRR_NAMESPACE, "remotes");
        remotes.setAttribute(XmlUtils.SCHEMA_LOCATION_ATTRIBUTE_NAME, XmlUtils.GIRR_SCHEMA_LOCATION + " " + XmlUtils.IRP_SCHEMA_LOCATION);
        remotes.setAttribute("xmlns:xsi", XmlUtils.XML_SCHEMA_INSTANCE);
        remotes.setAttribute("title", "Generated by " + Version.versionString);
        remotes.setAttribute("girrVersion", "1.x");
        doc.appendChild(remotes);
        Element protocolsElement = mkProtocols();
        remotes.appendChild(protocolsElement);
        Element remote = commandsToElement();
        remotes.appendChild(remote);
    }


    private Element commandsToElement() {
        Element remote = doc.createElementNS(XmlUtils.GIRR_NAMESPACE, "remote");
        remote.setAttribute("name", "remote");
        Element commandSet = doc.createElementNS(XmlUtils.GIRR_NAMESPACE, "commandSet");
        commandSet.setAttribute("name", "commandSet");
        remote.appendChild(commandSet);
        for (int i = 0; i< protocols.size(); i++)
            commandSet.appendChild(commandToElement(protocols.get(i), names != null ? names[i] : null, analyzer.cleanedIrSequence(i)));

        return remote;
    }

    private Element commandToElement(Protocol protocol, String name, IrSequence irSequence) {
        Element command = doc.createElementNS(XmlUtils.GIRR_NAMESPACE, "command");
        String commandName = name != null ? name : ("unnamed_" + Integer.toString(counter++));
        command.setAttribute("name", commandName);
        Element parameters = parametersToElement(protocol);
        command.appendChild(parameters);
        Element raw = rawToElement(irSequence);
        if (raw != null) {
            command.appendChild(raw);
            command.setAttribute("master", "raw");
        }
        return command;
    }

    private Element rawToElement(IrSequence irSequence) {
        Element raw = doc.createElementNS(XmlUtils.GIRR_NAMESPACE, "raw");
        raw.setAttribute("frequency",
                Integer.toString(analyzer.getFrequency() != null
                        ? analyzer.getFrequency().intValue() : (int) ModulatedIrSequence.DEFAULT_FREQUENCY));
        Element intro = doc.createElementNS(XmlUtils.GIRR_NAMESPACE, "intro");
        raw.appendChild(intro);
        Text content = doc.createTextNode(irSequence.toString(true, " ", "", ""));
        intro.appendChild(content);
        return raw;
    }

    private Element parametersToElement(Protocol protocol) {
        Element parameters = defsToElement(protocol.getDefinitions());
        Protocol withoutDefs = new Protocol(protocol.getGeneralSpec(), protocol.getBitspecIrstream(), new NameEngine(), null);

        parameters.setAttribute("protocol", formatProtocolnameFromHash(withoutDefs.hashCode()));
        return parameters;
    }

    private Element defsToElement(NameEngine definitions) {
        Element parameters = doc.createElementNS(XmlUtils.GIRR_NAMESPACE, "parameters");
        for (Map.Entry<String, Expression> definition : definitions) {
            Element parameter = doc.createElementNS(XmlUtils.GIRR_NAMESPACE, "parameter");
            parameter.setAttribute("name", definition.getKey());
            parameter.setAttribute("value", definition.getValue().toIrpString(radix));
            parameters.appendChild(parameter);
        }
        return parameters;
    }

    private Element mkProtocols() {
        protocolsWithoutDefs = new HashMap<>(4);
        for (Protocol protocol : protocols) {
            Protocol withoutDefs = new Protocol(protocol.getGeneralSpec(), protocol.getBitspecIrstream(), new NameEngine(), null);
            protocolsWithoutDefs.put(withoutDefs.hashCode(), withoutDefs);
        }
        Element protocolsElement = doc.createElementNS(XmlUtils.IRP_NAMESPACE, "irp:protocols");
        protocolsWithoutDefs.entrySet().forEach((proto) -> {
            Element protocolElement = doc.createElementNS(XmlUtils.IRP_NAMESPACE, "irp:protocol");
            protocolElement.setAttribute("name", formatProtocolnameFromHash(proto.getKey()));
            protocolsElement.appendChild(protocolElement);
            Element irp = doc.createElementNS(XmlUtils.GIRR_NAMESPACE, "irp:irp");
            irp.appendChild(doc.createCDATASection(proto.getValue().toIrpString(radix)));
            protocolElement.appendChild(irp);
        });
        return protocolsElement;
    }

    private Document getDocument() {
        return doc;
    }

    private String formatProtocolnameFromHash(Integer key) {
        return "p_" + Integer.toUnsignedString(key, 16);
    }
}
