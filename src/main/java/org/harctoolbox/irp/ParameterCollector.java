/*
Copyright (C) 2016 Bengt Martensson.

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.harctoolbox.ircore.IncompatibleArgumentException;

public class ParameterCollector implements Cloneable {

    private final static Logger logger = Logger.getLogger(ParameterCollector.class.getName());

    public final static long INVALID = -1L;

    private HashMap<String, BitwiseParameter> map;
    //private final List<String> needFinalCheck;

    public ParameterCollector() {
        map = new LinkedHashMap<>(8);
        //needFinalCheck = new ArrayList<>(2);
    }

    void add(String name, BitwiseParameter parameter) throws NameConflictException {
        logger.log(Level.FINER, "Assigning {0} = {1}", new Object[]{name, parameter});
        BitwiseParameter oldParameter = map.get(name);
        if (oldParameter != null) {
            if (oldParameter.isConsistent(parameter)) {
                oldParameter.aggregate(parameter);
            } else {
                logger.log(Level.FINE, "Name inconsistency: {0}, new value: {1}, old value: {2}", new Object[]{name, parameter.toString(), oldParameter.toString()});
                throw new NameConflictException(name);
            }
        } else {
            overwrite(name, parameter);
        }
    }

    void add(String name, long value) throws NameConflictException {
        add(name, new BitwiseParameter(value));
    }

    void add(String name, long value, long bitmask) throws NameConflictException {
        add(name, new BitwiseParameter(value, bitmask));
    }

    void overwrite(String name, BitwiseParameter parameter) {
        logger.log(Level.FINER, "Overwriting {0} = {1}", new Object[]{name, parameter});
        map.put(name, parameter);
    }

    public void overwrite(String name, long value) {
        overwrite(name, new BitwiseParameter(value));
    }

    BitwiseParameter get(String name) {
        return map.get(name);
    }

    public long getValue(String name) {
        return map.containsKey(name) ? map.get(name).getValue() : INVALID;
    }
/*
    public void checkConsistencyWith(NameEngine nameEngine) throws NameConflictException, UnassignedException, IrpSyntaxException, IncompatibleArgumentException {
        NameEngine extended = nameEngine.clone();
        addToNameEngine(extended);
        for (Map.Entry<String, BitwiseParameter> kvp : map.entrySet()) {
            String name = kvp.getKey();
            BitwiseParameter parameter = kvp.getValue();
            if (!parameter.isConsistent(extended.get(name).toNumber(extended)))
                throw new NameConflictException(name);
        }
    }*/

    NameEngine toNameEngine() throws IrpSyntaxException {
        NameEngine nameEngine = new NameEngine();
        for (Map.Entry<String, BitwiseParameter> kvp : map.entrySet()) {
            String name = kvp.getKey();
            BitwiseParameter parameter = kvp.getValue();
            if (!parameter.isEmpty())
                nameEngine.define(name, parameter.getValue());
        }
        return nameEngine;
    }

//    public void updateValues() throws IrpSyntaxException {
//        NameEngine simple = toNumericalNameEngine();
//        for (Map.Entry<String, BitwiseParameter> kvp : map.entrySet()) {
//            String name = kvp.getKey();
//            BitwiseParameter parameter = kvp.getValue();
//            if (parameter.expression != null) {
//                try {
//                    long val = parameter.expression.toNumber(simple);
//                    parameter.value = val;
//                    parameter.needsFinalChecking = false;
//                } catch (UnassignedException ex) {
//                    parameter.needsFinalChecking = true;
//                } catch (IncompatibleArgumentException ex) {
//                    logger.log(Level.SEVERE, null, ex);
//                }
//            }
//        }
//    }

    /*public NameEngine toNameEngine() throws IrpSyntaxException {
        NameEngine result = new NameEngine();
        for (Map.Entry<String, BitwiseParameter> kvp : map.entrySet()) {
            String name = kvp.getKey();
            BitwiseParameter parameter = kvp.getValue();
            if (parameter.value != null)
                result.define(name, parameter.value);
        }
        for (Map.Entry<String, BitwiseParameter> kvp : map.entrySet()) {
            String name = kvp.getKey();
            BitwiseParameter parameter = kvp.getValue();
            if (parameter.expression != null) {
                try {
                    long val = parameter.expression.toNumber(result);
                    result.define(name, val);
                } catch (UnassignedException ex) {
                    parameter.needsFinalChecking = true;
                } catch (IncompatibleArgumentException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
        }
        return result;
    }*/

//    public HashMap<String, Long> toHashMap() {
//        HashMap<String, Long> hashMap = new HashMap<>(map.size());
//
//        map.entrySet().stream().forEach((kvp) -> {
//            hashMap.put(kvp.getKey(), kvp.getValue().getValue());
//        });
//        return hashMap;
//    }

    /*public void add(NameEngine nameEngine) throws IrpSyntaxException, NameConflictException, UnassignedException, IncompatibleArgumentException {
        for (Map.Entry<String, BitwiseParameter> kvp : map.entrySet()) {
//            String name = kvp.getKey();
            if (!nameEngine.containsKey(kvp.getKey()))
//                if (nameEngine.get(name).toNumber(nameEngine) != kvp.getValue().value)
//                    throw new NameConflictException(name);
//            } else
                nameEngine.define(kvp.getKey(), kvp.getValue().getValue());
        }
    }*/

    void transferToNameEngine(NameEngine nameEngine) throws NameConflictException, IrpSyntaxException, IncompatibleArgumentException {
        for (Map.Entry<String, BitwiseParameter> kvp : map.entrySet()) {
            String name = kvp.getKey();
            BitwiseParameter parameter = kvp.getValue();
            if (nameEngine.containsKey(name)) {
                try {
                    Expression expression = nameEngine.get(name);
                    long val = expression.toNumber(nameEngine);
                    if (!parameter.isConsistent(val))
                        throw new NameConflictException(name);
                } catch (UnassignedException ex) {
                }
            }
            nameEngine.define(kvp.getKey(), kvp.getValue().getValue());
        }
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder(100);
        str.append("{");
        map.entrySet().stream().forEach((kvp) -> {
            if (str.length() > 1)
                str.append(";");
            str.append(kvp.getKey()).append("=").append(kvp.getValue().toString());
        });
        return str.append("}").toString();
    }

    @Override
    @SuppressWarnings("CloneDeclaresCloneNotSupported")
    public ParameterCollector clone() {
        ParameterCollector result;
        try {
            result = (ParameterCollector) super.clone();
        } catch (CloneNotSupportedException ex) {
            throw new InternalError(ex);
        }
        result.map = new LinkedHashMap<>(10);
        map.entrySet().stream().forEach((kvp) -> {
            result.map.put(kvp.getKey(), kvp.getValue().clone());
        });
        return result;
    }

//    void checkConsistency(NameEngine nameEngine, NameEngine definitions) {
//        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
//    }

    boolean isConsistent(String name, long value) {
        BitwiseParameter param = get(name);
        return param.isConsistent(value);
    }
}
