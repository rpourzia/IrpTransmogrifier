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

package org.harctoolbox.analyze;

import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.harctoolbox.ircore.InvalidArgumentException;
import org.harctoolbox.ircore.IrCoreUtils;
import org.harctoolbox.ircore.IrSequence;
import org.harctoolbox.ircore.IrSignal;
import org.harctoolbox.ircore.ModulatedIrSequence;
import org.harctoolbox.ircore.OddSequenceLengthException;
import org.harctoolbox.ircore.ThisCannotHappenException;
import org.harctoolbox.irp.BitDirection;
import org.harctoolbox.irp.GeneralSpec;
import org.harctoolbox.irp.Protocol;

public final class Analyzer extends Cleaner {

    private static final Logger logger = Logger.getLogger(Analyzer.class.getName());

    public static int[] mkIndices(Collection<? extends IrSequence> irSequenceList) {
        int[] indices = new int[irSequenceList.size()];
        int i = 0;
        for (IrSequence irSequence : irSequenceList) {
            indices[i] = irSequence.getLength() + (i > 0 ? indices[i - 1] : 0);
            i++;
        }
        return indices;
    }

    public static Protocol selectBestProtocol(List<Protocol> protocols) {
        Protocol bestSoFar = null;
        int weight = Integer.MAX_VALUE;
        for (Protocol protocol : protocols) {
            int protocolWeight = protocol.weight();
            if (protocolWeight < weight) {
                bestSoFar = protocol;
                weight = protocolWeight;
            }
        }
        return bestSoFar;
    }

    private List<Burst> pairs;
    private final RepeatFinder.RepeatFinderData[] repeatFinderData;
    private Double frequency;
    private List<Burst> sortedBursts;

    public Analyzer(IrSignal irSignal, Double absoluteTolerance, Double relativeTolerance) throws InvalidArgumentException {
        this(irSignal.toIrSequences(), mkIndices(irSignal.toIrSequences()), true, irSignal.getFrequency(), false, absoluteTolerance, relativeTolerance);
    }

    public Analyzer(IrSequence irSequence, Double frequency, boolean invokeRepeatFinder, Double absoluteTolerance, Double relativeTolerance) throws InvalidArgumentException {
        this(Arrays.asList(irSequence), frequency, invokeRepeatFinder, absoluteTolerance, relativeTolerance);
    }

    public Analyzer(Collection<? extends IrSequence> irSequenceList, Double frequency, boolean invokeRepeatFinder, Double absoluteTolerance, Double relativeTolerance) throws InvalidArgumentException {
        this(irSequenceList, mkIndices(irSequenceList), false, frequency, invokeRepeatFinder, absoluteTolerance, relativeTolerance);
    }

    private Analyzer(Collection<? extends IrSequence> irSequenceList, int[] indices, boolean signalMode, Double frequency, boolean invokeRepeatFinder, Double absoluteTolerance, Double relativeTolerance) throws InvalidArgumentException {
        super(IrSequence.toInts(irSequenceList), indices, signalMode, absoluteTolerance, relativeTolerance);
        if (frequency == null)
            logger.log(Level.FINE, String.format(Locale.US, "No frequency given, assuming default frequency = %d Hz", (int) ModulatedIrSequence.DEFAULT_FREQUENCY));
        this.frequency = frequency;
        repeatFinderData = new RepeatFinder.RepeatFinderData[irSequenceList.size()];
        for (int i = 0; i < irSequenceList.size(); i++)
            repeatFinderData[i] = getRepeatFinderData(invokeRepeatFinder, i);
        createPairs();
    }

    public Analyzer(ModulatedIrSequence irSequence, boolean invokeRepeatFinder, Double absoluteTolerance, Double relativeTolerance) throws InvalidArgumentException {
        this(irSequence, irSequence.getFrequency(), invokeRepeatFinder, absoluteTolerance, relativeTolerance);
    }

    public Analyzer(IrSequence irSequence, boolean invokeRepeatFinder) throws InvalidArgumentException {
        this(irSequence, null, invokeRepeatFinder, IrCoreUtils.DEFAULT_ABSOLUTE_TOLERANCE, IrCoreUtils.DEFAULT_RELATIVE_TOLERANCE);
    }

    public Analyzer(IrSequence irSequence, Double absoluteTolerance, Double relativeTolerance) throws InvalidArgumentException {
        this(irSequence, null, false, absoluteTolerance, relativeTolerance);
    }

    public Analyzer(IrSequence irSequence) throws InvalidArgumentException {
        this(irSequence, null, false, null, null);
    }

    public Analyzer(int[] data) throws OddSequenceLengthException, InvalidArgumentException {
        this(new IrSequence(data), null, false, null, null);
    }

    public Analyzer(IrSequence irSequence, Double frequency, boolean invokeRepeatFinder) throws InvalidArgumentException {
        this(irSequence, frequency, invokeRepeatFinder, null, null);
    }

    public Analyzer(IrSignal irSignal) throws InvalidArgumentException {
       this(irSignal, null, null);
    }

    /**
     * Return bursts order after their frequency.
     * @param i
     * @return
     */
    public Burst getBurst(int i) {
        return pairs.get(i);
    }

    /**
     * Return bursts ordered lexicographically.
     * @param i
     * @return
     */
    public Burst getSortedBurst(int i) {
        return sortedBursts.get(i);
    }

    private RepeatFinder.RepeatFinderData getRepeatFinderData(boolean invokeRepeatFinder, int number) {
        int beg = getSequenceBegin(number);
        int length = getSequenceLength(number);
        try {
            return invokeRepeatFinder ? new RepeatFinder(toDurations(beg, length)).getRepeatFinderData()
                    : new RepeatFinder.RepeatFinderData(length);
        } catch (OddSequenceLengthException ex) {
            throw new ThisCannotHappenException(ex);
        }
    }

    RepeatFinder.RepeatFinderData getRepeatFinderData(int number) {
        return repeatFinderData[number];
    }

    public IrSignal repeatReducedIrSignal(int number) {
        IrSequence intro;
        IrSequence repeat;
        IrSequence ending;
        try {
            if (isSignalMode()) {
                intro = new IrSequence(toDurations(getSequenceBegin(0), getSequenceLength(0)));
                repeat = new IrSequence(toDurations(getSequenceBegin(1), getSequenceLength(1)));
                ending = new IrSequence(toDurations(getSequenceBegin(2), getSequenceLength(2)));
            } else {
                int begin = getSequenceBegin(number);
                RepeatFinder.RepeatFinderData repeatfinderData = getRepeatFinderData(number);
                intro = new IrSequence(toDurations(begin, repeatfinderData.getBeginLength()));
                repeat = new IrSequence(toDurations(begin + repeatfinderData.getBeginLength(), repeatfinderData.getRepeatLength()));
                ending = new IrSequence(toDurations(begin + repeatfinderData.getEndingStart(), repeatfinderData.getEndingLength()));
            }
            return new IrSignal(intro, repeat, ending, frequency);
        } catch (OddSequenceLengthException ex) {
            throw new ThisCannotHappenException(ex);
        }
    }

    private void createPairs() {
        pairs = new ArrayList<>(16);
        getFlashes().stream().forEach((flash) -> {
            getGaps().stream().filter((gap) -> (getNumberPairs(flash, gap) > 0)).forEach((gp) -> {
                pairs.add(new Burst(flash, gp));
            });
        });
        Collections.sort(pairs, (a, b) -> getNumberPairs(b) - getNumberPairs(a));
        sortedBursts = new ArrayList<>(pairs);
        sortedBursts.sort((a, b) -> Burst.compare(a, b));
    }


    private List<Class<?>> selectDecoderClasses(String decoderPattern, boolean regexp) throws NoDecoderMatchException {
        List<Class<?>> decoders = regexp ? selectDecoderClassesRegexp(decoderPattern) : selectDecoderClassesSubstring(decoderPattern);
        if (decoders.isEmpty())
            throw new NoDecoderMatchException(decoderPattern, regexp);
        return decoders;
    }

    private List<Class<?>> selectDecoderClassesRegexp(String decoderPattern) {
        Pattern pattern = decoderPattern != null ? Pattern.compile(decoderPattern, Pattern.CASE_INSENSITIVE) : null;
        List<Class<?>> decoders = new ArrayList<>(AbstractDecoder.NUMBERDECODERS);
        for (Class<?> decoderClass : AbstractDecoder.decoders)
            if (pattern == null || pattern.matcher(decoderClass.getSimpleName()).matches())
                decoders.add(decoderClass);
        return decoders;
    }

    private List<Class<?>> selectDecoderClassesSubstring(String decoderPattern) {
        List<Class<?>> decoders = new ArrayList<>(AbstractDecoder.NUMBERDECODERS);
        for (Class<?> decoderClass : AbstractDecoder.decoders)
            if (decoderPattern == null
                    || decoderClass.getSimpleName().regionMatches(true, 0, decoderPattern, 0, decoderPattern.length()))
                decoders.add(decoderClass);
        return decoders;
    }

    private List<AbstractDecoder> setupDecoders(Analyzer.AnalyzerParams params, String decoderPattern, boolean regexp) throws NoDecoderMatchException {
        List<Class<?>> decoderClasses = selectDecoderClasses(decoderPattern, regexp);
        List<AbstractDecoder> decoders = new ArrayList<>(AbstractDecoder.NUMBERDECODERS);
        decoderClasses.forEach((decoderClass) -> {
            try {
                Constructor<?> constructor = decoderClass.getConstructor(Analyzer.class, AnalyzerParams.class);
                AbstractDecoder decoder = (AbstractDecoder) constructor.newInstance(this, params);
                decoders.add(decoder);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InstantiationException ex) {
                // consider this as programming error
                throw new ThisCannotHappenException(ex);
            } catch (InvocationTargetException ex) {
                // Likely not a fatal problem, the decoder just did not accept the data.
                logger.log(Level.FINE, String.format("Decoder %1$s failed: %2$s(%3$s)", decoderClass.getSimpleName(), ex.getTargetException().getClass().getSimpleName(), ex.getTargetException().getMessage()));
            }
        });
        return decoders;
    }

//    /**
//     * @return the timebase
//     */
//    public int getTimebase() {
//        return timebase;
//    }

    /**
     * @return the pairs
     */
    public List<Burst> getPairs() {
        return Collections.unmodifiableList(pairs);
    }

    public String getName(Burst pair) {
        return getName(pair.getFlashDuration()) + getName(pair.getGapDuration());
    }

    public int getNumberPairs(Burst pair) {
        return getNumberPairs(pair.getFlashDuration(), pair.getGapDuration());
    }

    public Double getFrequency() {
        return frequency;
    }

    public List<List<Protocol>> searchAllProtocols(AnalyzerParams params, String decoderPattern, boolean regexp) throws NoDecoderMatchException {
        List<AbstractDecoder> decoders = setupDecoders(params, decoderPattern, regexp);
        List<List<Protocol>> result = new ArrayList<>(getNoSequences());
        for (int i = 0; i < getNoSequences(); i++)
            result.add(searchProtocols(decoders, i));

        return result;
    }

    public List<Protocol> searchBestProtocol(AnalyzerParams params, String decoderPattern, boolean regexp) throws NoDecoderMatchException {
        List<AbstractDecoder> decoders = setupDecoders(params, decoderPattern, regexp);
        List<Protocol> result = new ArrayList<>(getNoSequences());
        for (int i = 0; i < getNoSequences(); i++) {
            Protocol best = searchBestProtocol(decoders, i);
            if (best != null)
                result.add(best);
        }

        return result;
    }

    public List<Protocol> searchBestProtocol(AnalyzerParams analyzerParams) throws NoDecoderMatchException {
        return searchBestProtocol(analyzerParams, null, false);
    }

    public List<Protocol> searchProtocols(List<AbstractDecoder> decoders, int number) {
        List<Protocol> protocols = new ArrayList<>(decoders.size());
        decoders.forEach((decoder) -> {
            try {
                Protocol protocol = decoder.parse(number, isSignalMode());
                protocols.add(protocol);
                logger.log(Level.FINE, "{0}: {1} w = {2}", new Object[]{decoder.name(), protocol.toIrpString(10), protocol.weight()});
            } catch (DecodeException ex) {
                logger.log(Level.FINE, "{0}: {1}", new Object[]{decoder.name(), ex.getMessage()});
            } catch (AnalyzerParams.TooFewParameterNamesException ex) {
                logger.log(Level.FINE, "{0}: Too few parameter names", new Object[]{decoder.name()});
            }
        });
        return protocols;
    }

    public Protocol searchBestProtocol(List<AbstractDecoder> decoders, int number) {
        List<Protocol> protocols = searchProtocols(decoders, number);
        return selectBestProtocol(protocols);
    }

    public void printStatistics(PrintStream out, AnalyzerParams params) {
        out.println("Timebase: " + getRealTimebase(params.getTimebase(), params.burstPrefs.getMaxRoundingError()));
        out.println();

        out.println("Gaps:");
        this.getGaps().stream().forEach((d) -> {
            out.println(this.getName(d) + ":\t" + d + "\t" + multiplierString(d, params.getTimebase(), params.burstPrefs) + "\t" + this.getNumberGaps(d));
        });
        out.println();

        out.println("Flashes:");
        this.getFlashes().stream().forEach((d) -> {
            out.println(this.getName(d) + ":\t" + d + "\t" + multiplierString(d, params.getTimebase(), params.burstPrefs) + "\t" + this.getNumberFlashes(d));
        });
        out.println();

        out.println("Pairs:");
        this.getPairs().stream().forEach((pair) -> {
            out.println(this.getName(pair) + ":\t" + this.getNumberPairs(pair));
        });
    }

    private double getRealTimebase(Double timebase, double relativeTolerance) {
        return timebase != null ? timebase : getTimeBaseFromData(relativeTolerance);
    }

    private String multiplierString(int us, Double timebase, Burst.Preferences burstPrefs) {
        double tick = getRealTimebase(timebase, burstPrefs.getMaxRoundingError());
        Integer mult = Burst.multiplier(us, tick, burstPrefs);
        return mult != null ? "= " + mult.toString() + "*" + Long.toString(Math.round(tick)) + "  " : "\t";
    }

    public RepeatFinder.RepeatFinderData repeatFinderData(int i) {
        return repeatFinderData[i];
    }

    @SuppressWarnings("null")
    double getTimeBaseFromData(AnalyzerParams params) {
        Objects.requireNonNull(params);
        return params.getTimebase() != null
                ? params.getTimebase()
                : getTimeBaseFromData(params.getBurstPrefs().getMaxRoundingError());
    }

    public static class AnalyzerParams {
        private final Double frequency;
        private final Double timebase;
        private final boolean preferPeriods;
        private final BitDirection bitDirection;
        private final boolean useExtents;
        private final boolean invert;
        private final List<Integer> parameterWidths;
        private final int maxParameterWidth;
        private final Burst.Preferences burstPrefs;
        private List<String> parameterNames;

        public AnalyzerParams(Double frequency, String timeBaseString, BitDirection bitDirection, boolean useExtents, List<Integer> parameterWidths, boolean invert) {
            this(frequency, timeBaseString, bitDirection, useExtents, parameterWidths, 32, invert, new Burst.Preferences(), new ArrayList<>(0));
        }
        
        public AnalyzerParams() {
            this(null, null, BitDirection.lsb, false, null, 32, false, new Burst.Preferences(), new ArrayList<>(0));
        }

        /**
         *
         * @param frequency
         * @param timeBaseString
         * @param bitDirection
         * @param useExtents
         * @param parameterWidths
         * @param maxParameterWidth
         * @param invert
         * @param burstPrefs
         * @param parameterNames List of narameter names to used; must consist of valid Name-s.
         */
        public AnalyzerParams(Double frequency, String timeBaseString, BitDirection bitDirection, boolean useExtents,
                List<Integer> parameterWidths, int maxParameterWidth, boolean invert, Burst.Preferences burstPrefs, List<String> parameterNames) {
            this.frequency = frequency;
            this.bitDirection = bitDirection;
            this.useExtents = useExtents;
            this.invert = invert;
            this.burstPrefs = burstPrefs;
            this.parameterWidths = parameterWidths == null ? new ArrayList<>(0) : parameterWidths;
            this.maxParameterWidth = maxParameterWidth;
            this.parameterNames = parameterNames;

            if (timeBaseString == null || timeBaseString.isEmpty()) {
                timebase = null;
                preferPeriods = false;
            } else {
                preferPeriods = timeBaseString.endsWith("p");
                if (preferPeriods && frequency == null)
                    logger.warning("Period based timing selected, but no explicit frequency given.");
                String str = (timeBaseString.endsWith("p") || timeBaseString.endsWith("u"))
                        ? timeBaseString.substring(0, timeBaseString.length() - 1)
                        : timeBaseString;
                double timeBaseNumber = Double.parseDouble(str);
                timebase = preferPeriods
                        ? IrCoreUtils.seconds2microseconds(timeBaseNumber / ModulatedIrSequence.getFrequencyWithDefault(frequency))
                        : timeBaseNumber;
            }
        }

        Burst.Preferences getBurstPrefs() {
            return burstPrefs;
        }

        @SuppressWarnings("null")
        public int getNoBitsLimit(int noPayload) {
            int tableLimit = (parameterWidths == null || noPayload >= parameterWidths.size()) ? Integer.MAX_VALUE : parameterWidths.get(noPayload);
            return Math.min(maxParameterWidth, tableLimit);
        }

        /**
         * @return the bitDirection
         */
        public BitDirection getBitDirection() {
            return bitDirection;
        }

        /**
         * @return the useExtents
         */
        public boolean isUseExtents() {
            return useExtents;
        }

        /**
         * @param i
         * @return the parameterWidths
         */
        public Integer getParameterWidth(int i) {
            return parameterWidths.get(i);
        }

        public int getMaxParameterWidth() {
            return maxParameterWidth;
        }

        /**
         * @return the frequency
         */
        public double getFrequency() {
            return frequency;
        }

        /**
         * @return the timebase
         */
        public Double getTimebase() {
            return timebase;
        }

        /**
         * @return the preferPeriods
         */
        public boolean isPreferPeriods() {
            return preferPeriods;
        }

        public GeneralSpec getGeneralSpec(double otherTimebase) {
            return new GeneralSpec(bitDirection, otherTimebase, frequency);
        }

        /**
         * @return the invert
         */
        public boolean isInvert() {
            return invert;
        }

        String mkName(int n) {
            if (n < parameterNames.size())
                return parameterNames.get(n);

            String name = Cleaner.mkName(n - parameterNames.size());
            if (parameterNames.contains(name))
                throw new TooFewParameterNamesException();
            return name;
        }

        public static class TooFewParameterNamesException extends RuntimeException {

            public TooFewParameterNamesException() {
                super();
            }
        }
    }
}
