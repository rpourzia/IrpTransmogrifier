/*
Copyright (C) 2017, 2018 Bengt Martensson.

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.harctoolbox.ircore.ThisCannotHappenException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Implementation of Definitions in Chapter 10 and Assignments in Chapter 11; these are not independent objects.
 *
 */

public final class NameEngine extends IrpObject implements AggregateLister, Iterable<Map.Entry<String, Expression>> {
    private final static int WEIGHT = 0;

    private final static Logger logger = Logger.getLogger(NameEngine.class.getName());

    @SuppressWarnings("PackageVisibleField")
    public static final NameEngine EMPTY = new NameEngine();

    public static NameEngine parseLoose(String str) {
        if (str == null || str.trim().isEmpty())
            return new NameEngine();

        String payload = str.trim().replaceFirst("^\\{", "").replaceFirst("\\}$", "").replaceAll("\\s*=\\s*", "=");
        String[] definitions = payload.split("[\\s,;]+");
        return parse(definitions);
    }

    public static NameEngine parse(String[] definitions) {
        NameEngine nameEngine = new NameEngine();
        for (String definition : definitions) {
            ParserDriver parserDriver = new ParserDriver(definition);
            nameEngine.parseDefinition(parserDriver.getParser().definition());
        }
        return nameEngine;
    }

    private static Element mkElement(Document document, Map.Entry<String, Expression> definition) {
        Element element = document.createElement("Definition");
        try {
            element.appendChild(new Name(definition.getKey()).toElement(document));
        } catch (InvalidNameException ex) {
            throw new ThisCannotHappenException(ex);
        }
        element.appendChild(definition.getValue().toElement(document));
        return element;
    }

    private static HashMap<String, Expression> mapConvert(Map<String, Long> numericalParameters) {
        HashMap<String, Expression> result = new HashMap<>(numericalParameters.size());
        numericalParameters.entrySet().forEach((kvp) -> {
            result.put(kvp.getKey(), new NumberExpression(kvp.getValue()));
        });
        return result;
    }

    private Map<String, Expression> map;

    public NameEngine(Map<String, Long> numericalParameters) {
        this(null, mapConvert(numericalParameters));
    }

    public NameEngine() {
        this(0);
    }

    public NameEngine(int initialCapacity) {
        this(null, new HashMap<String, Expression>(initialCapacity));
    }

    private NameEngine(IrpParser.DefinitionsContext ctx, Map<String, Expression> map) {
        super(ctx);
        this.map = map;
    }

    private NameEngine(IrpParser.DefinitionsContext ctx, int initialCapacity) {
        this(ctx, new LinkedHashMap<>(initialCapacity));
    }

    private NameEngine(IrpParser.DefinitionsContext ctx) {
        this(ctx, 4);
        parseDefinitions(ctx.definitions_list());
    }

    public NameEngine(String str) throws InvalidNameException {
        this(new ParserDriver(str));
    }

    private NameEngine(ParserDriver parserDriver) throws InvalidNameException {
        this(parserDriver.getParser().definitions());
    }

    public NameEngine(NameEngine orig) {
        this(null, new HashMap<String, Expression>(orig.map));
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + Objects.hashCode(this.map);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final NameEngine other = (NameEngine) obj;
        return Objects.equals(this.map, other.map);
    }

    public int size() {
        return map.size();
    }

    public boolean numericallyEquals(Object object) {
        if (object == this)
            return true;
        if (!(object instanceof NameEngine))
            return false;

        NameEngine other = (NameEngine) object;
        if (other.size() != this.size())
            return false;

        for (Map.Entry<String, Expression> kvp : map.entrySet()) {
            try {
                String name = kvp.getKey();
                long value = kvp.getValue().toLong(this);
                Expression expr = other.get(name);
                if (value != expr.toLong(other)) {
                    logger.log(Level.INFO, "Variable \"{0}\" valued {1} instead of {2}", new Object[]{name, value, expr.toLong(other)});
                    return false;
                }
            } catch (NameUnassignedException ex) {
                return false;
            }
        }
        return true;
    }

    public boolean numericallyEquals(Map<String, Long> other) {
        if (other == null)
            return false;
        if (other.size() != this.size())
            return false;

        for (Map.Entry<String, Expression> kvp : map.entrySet()) {
            try {
                String name = kvp.getKey();
                long value = kvp.getValue().toLong(this);

                if (value != other.get(name))
                    return false;
            } catch (NameUnassignedException ex) {
                logger.log(Level.SEVERE, null, ex);
                return false;
            }
        }
        return true;
    }

    public boolean numericallyEquals(NameEngine other) {
        try {
            return numericallyEquals(other.toMap());
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean numericallyEquals(Decoder.Decode other) {
        try {
            return numericallyEquals(other.getMap());
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public Iterator<Map.Entry<String, Expression>> iterator() {
        return map.entrySet().iterator();
    }

    private void define(String name, IrpParser.ExpressionContext ctx) throws InvalidNameException {
        define(name, Expression.newExpression(ctx));
    }

    public void define(String name, Expression expression) throws InvalidNameException {
        Name.checkName(name);
        map.put(name, expression);
    }

    public void define(String name, java.lang.Number value) throws InvalidNameException {
        define(name, new NumberExpression(value));
    }

    /**
     * Invoke the parser on the supplied argument, and stuff the result into the name engine.
     *
     * @param str String to be parsed, like "{C = F*4 + D + 3}".
     */
    public void parseDefinitions(String str) {
        ParserDriver parserDriver = new ParserDriver(str);
        parseDefinitions(parserDriver.getParser().definitions());
    }

    public void parseDefinitions(IrpParser.DefinitionsContext ctx /* DEFINITIONS */) {
        parseDefinitions(ctx.definitions_list());
    }

    private void parseDefinition(IrpParser.DefinitionContext ctx /* DEFINITION */) {
        try {
            define(ctx.name().getText(), ctx.expression());
        } catch (InvalidNameException ex) {
            throw new ThisCannotHappenException(ex);
        }
    }

    private void parseDefinitions(IrpParser.Definitions_listContext definitions_list) {
        parseDefinitions(definitions_list.definition());
    }

    private void parseDefinitions(List<IrpParser.DefinitionContext> list) {
        list.forEach((def) -> {
            parseDefinition(def);
        });
    }

    /**
     * Returns the expression associated to the name given as parameter.
     * @param name
     * @return
     * @throws org.harctoolbox.irp.NameUnassignedException
     */
    public Expression get(String name) throws NameUnassignedException {
        //Debug.debugNameEngine("NameEngine: " + name + (map.containsKey(name) ? (" = " + map.get(name).toStringTree()) : "-"));
        Expression expression = map.get(name);
        if (expression == null)
            throw new NameUnassignedException(name);
        return expression;
    }

    public Expression get(Name name) throws NameUnassignedException {
        return get(name.toString());
    }

    public Expression getPossiblyNull(String name) {
        return map.get(name);
    }

    public long toLong(String name) throws NameUnassignedException {
        Expression expression = get(name);
        return expression.toLong(this);
    }

    public boolean containsKey(String name) {
        return map.containsKey(name);
    }

    void add(NameEngine definitions) {
        map.putAll(definitions.map);
    }

    @Override
    public String toIrpString(int radix) {
        return toIrpString(radix, "");
    }

    String toIrpString(int radix, String separator) {
        if (map.isEmpty())
            return "";
        StringJoiner stringJoiner = new StringJoiner("," + separator, "{", "}");
        map.keySet().stream().sorted().forEach((key) -> {
            stringJoiner.add(key + "=" + map.get(key).toIrpString(radix));
        });
        return stringJoiner.toString();
    }


    @Override
    public Element toElement(Document document) {
        Element root = document.createElement("Definitions"); // do not use super!
        map.entrySet().forEach((definition) -> {
                root.appendChild(mkElement(document, definition));
        });
        return root;
    }


    public Map<String, Long> toMap() {
        HashMap<String, Long> result = new HashMap<>(map.size());
        map.entrySet().forEach((kvp) -> {
            try {
                result.put(kvp.getKey(), kvp.getValue().toLong(this));
            } catch (NameUnassignedException ex) {
                throw new ThisCannotHappenException(ex);
            }
        });
        return result;
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    void addBarfByConflicts(NameEngine nameEngine) throws ParameterInconsistencyException {
        for (Map.Entry<String, Expression> kvp : nameEngine.map.entrySet()) {
            String name = kvp.getKey();
            Expression val = kvp.getValue();
            if (map.containsKey(name)) {
                try {
                    if (map.get(name).toLong(this) != val.toLong(nameEngine)) {
                        logger.log(Level.FINER, "Name conflict {0}", name);
                        throw new ParameterInconsistencyException(name, map.get(name).toLong(this), val.toLong(nameEngine));
                    }
                } catch (NameUnassignedException ex) {
                    throw new ThisCannotHappenException(ex);
                }
            } else
                map.put(name, val);
        }
    }

    @Override
    public int weight() {
        return WEIGHT;
    }

    @Override
    public Map<String, Object> propertiesMap(GeneralSpec generalSpec, NameEngine nameEngine) {
        Map<String, Object> result = new HashMap<>(2);
        result.put("kind", this.getClass().getSimpleName());
        List<Map<String, Object>> list = new ArrayList<>(map.size());
        result.put("list", list);
        map.entrySet().stream().map((kvp) -> {
            Map<String, Object> m = new HashMap<>(2);
            m.put("name", kvp.getKey());
            m.put("expression", kvp.getValue().propertiesMap(true, generalSpec, nameEngine));
            return m;
        }).forEachOrdered((m) -> {
            list.add(m);
        });
        return result;
    }

    Map<String, Long> getNumericLiterals() {
        HashMap<String, Long> result = new HashMap<>(this.size());
        map.entrySet().forEach((kvp) -> {
            String name = kvp.getKey();
            Expression exp = kvp.getValue();
            long val;
            try {
                val = exp.toLong();
                result.put(name, val);
            } catch (NameUnassignedException ex) {
            }
        });
        return result;
    }

    NameEngine remove(Iterable<String> names) {
        NameEngine result = new NameEngine(this);
        names.forEach((key) -> result.map.remove(key));
        return result;
    }
}
