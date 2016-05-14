/*
Copyright (C) 2011, 2016 Bengt Martensson.

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

/**
 * This class implements Repeatmarker as per Chapter 8.
 */
public class RepeatMarker {

    private int min;
    private int max;

    public RepeatMarker(String str) {
        this((new ParserDriver(str)).getParser().repeat_marker());
    }

    public RepeatMarker(IrpParser.Repeat_markerContext ctx) {
        String ch = ctx.getChild(0).getText();
        switch (ch) {
            case "*":
                min = 0;
                max = Integer.MAX_VALUE;
                break;
            case "+":
                min = 1;
                max = Integer.MAX_VALUE;
                break;
            default:
                min = Integer.parseInt(ch);
                max = ctx.getChildCount() > 1 ? max = Integer.MAX_VALUE : min;
                break;
        }
    }

    public RepeatMarker() {
        min = 1;
        max = 1;
    }

    public RepeatMarker(char ch) {
        this(Character.toString(ch));
    }

    public boolean isInfinite() {
        return max == Integer.MAX_VALUE;
    }
/*
    public boolean is(int n) {
        return n == min && n == max;
    }

    public boolean is(String s) {
        int n;
        try {
            n = Integer.parseInt(s);
            if (n == min && n == max)
                return true;
        } catch (NumberFormatException ex) {
            // not an error
        }
        if (s.endsWith("+")) {
            try {
                n = Integer.parseInt(s.substring(0, s.length()-1));
                if (n == min && max == Integer.MAX_VALUE)
                    return true;
            } catch (NumberFormatException ex) {
                // not an error
            }
        }

        return
                (s.equals("*"))    ? min == 0 && max == Integer.MAX_VALUE
                : (s.equals("+"))  ? min == 1 && max == Integer.MAX_VALUE
                : (s.equals(" "))  ? min == 1 && max != 1
                : (s.equals("n"))  ? min == max && max != Integer.MAX_VALUE
                : (s.equals("n+")) ? min != Integer.MAX_VALUE && max == Integer.MAX_VALUE
                : false;
    }
*/
    @Override
    public String toString() {
        return
                //(min == 1 && max == 1) ? ""
                  (min == 0 && max == Integer.MAX_VALUE) ? "*"
                : (min == 1 && max == Integer.MAX_VALUE) ? "+"
                : (min == max) ? Integer.toString(min)
                : (max == Integer.MAX_VALUE) ? Integer.toString(min) + "+"
                : "??";
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        //System.out.println((new RepeatMarker("")));
        System.out.println((new RepeatMarker("*")));
        System.out.println((new RepeatMarker('+')));
        System.out.println((new RepeatMarker("1+")));
        System.out.println((new RepeatMarker("0+")));
        System.out.println((new RepeatMarker("7")));
        System.out.println((new RepeatMarker("17+")));
        System.out.println((new RepeatMarker("\t7+   ")));
    }

    /**
     * @return the min
     */
    public int getMin() {
        return min;
    }

    /**
     * @return the max
     */
    public int getMax() {
        return max;
    }
}