/*
* Copyright (C) 2020 Information Retrieval Group at Universidad Autónoma
* de Madrid, http://ir.ii.uam.es.
*
* This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/
package es.uam.ir.ranksys.rec.runner.fast;

import es.uam.eps.ir.ranksys.fast.index.FastItemIndex;
import es.uam.eps.ir.ranksys.fast.index.FastUserIndex;
import es.uam.eps.ir.ranksys.fast.preference.FastPreferenceData;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.ranksys.formats.parsing.Parser;

/**
 *
 * @author Rocío Cañamares
 * @author Pablo Castells
 *
 */
public class FastSamplers {

    static Random rnd = new Random();

    /**
     * 
     * @param <U>
     * @param <I>
     * @param sampler
     * @param trainData
     * @param itemIndex
     * @param outputPath
     * @throws FileNotFoundException 
     */
    public static <U, I> void write(Function<U, IntPredicate> sampler, FastPreferenceData<U, I> trainData, FastItemIndex<Long> itemIndex, String outputPath) throws FileNotFoundException{
        try (PrintStream out = new PrintStream(outputPath)) {
            trainData.getUsersWithPreferences().map(user -> {
                IntPredicate userSampler = sampler.apply(user);
                List<String> itemSet = itemIndex.getAllIidx()
                        .filter(iidx -> userSampler.test(iidx))
                        .mapToObj(iidx -> itemIndex.iidx2item(iidx) + "")
                        .collect(Collectors.toList());
                return user + ":\n" + String.join("\n", itemSet);
            }).forEachOrdered(output -> out.println(output));
        }
    }

    /**
     * 
     * @param <U>
     * @param <I>
     * @param userIndex
     * @param itemIndex
     * @param dataPath
     * @param uParser
     * @param iParser
     * @return 
     */
    public static <U, I> Function<U, IntPredicate> read(
            FastUserIndex<U> userIndex,
            FastItemIndex<I> itemIndex,
            String dataPath,
            Parser<U> uParser,
            Parser<I> iParser) {
        BufferedReader reader = null;
        try {
            Map<U, IntSet> mapSets = userIndex.getAllUsers().collect(Collectors.toMap(user -> user, user -> new IntOpenHashSet()));
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(dataPath)));
            String line;
            U u = null;
            while ((line = reader.readLine()) != null) {
                if (line.length() < 1)  continue;
                if (line.endsWith(":")) {
                    u = uParser.parse(line.replaceAll(":", ""));
                } else {
                    mapSets.get(u).add(itemIndex.item2iidx(iParser.parse(line)));
                }
            }
            reader.close();
            return user -> {
                IntSet set = mapSets.get(user);
                return iidx -> set.contains(iidx);
            };
        } catch (IOException ex) {
            Logger.getLogger(FastSamplers.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    /**
     * 
     * @param <U>
     * @param <I>
     * @param trainData
     * @param mapSets
     * @param n
     * @return 
     */
    public static <U, I> Function<U, IntPredicate> uniform(FastPreferenceData<U, I> trainData, Map<U, IntSet> mapSets, int n) {
        IntFunction<Double> weight = iidx -> 1.0;
        return sample(trainData, mapSets, n, weight);
    }

    /**
     * 
     * @param <U>
     * @param <I>
     * @param trainData
     * @param mapSets
     * @param n
     * @param weight
     * @return 
     */
    public static <U, I> Function<U, IntPredicate> sample(FastPreferenceData<U, I> trainData, Map<U, IntSet> mapSets, int n, IntFunction<Double> weight) {
        Map<U, IntSet> mapKSets;
        if (n >= trainData.numItems()) {
            IntSet kSet = new IntOpenHashSet();
            trainData.getAllIidx().filter(iidx -> weight.apply(iidx) > 0).forEach(kSet::add);
            mapKSets = trainData.getAllUsers().parallel().collect(Collectors.toMap(user -> user, user -> kSet));
        } else {
            mapKSets = trainData.getAllUsers().parallel().collect(Collectors.toMap(
                    user -> user,
                    user -> {
                        IntSet testSet = mapSets.get(user);

                        int nItems = trainData.numItems();
                        double items[] = new double[nItems];
                        double sum = trainData.getAllIidx()
                        .filter(iidx -> !testSet.contains(iidx))
                        .mapToDouble(iidx -> {
                            items[iidx] = weight.apply(iidx);
                            return items[iidx];
                        }).sum();

                        IntSet kSet = new IntOpenHashSet();
                        for (int i = 0; i < n && sum > 0; i++) {
                            double p = rnd.nextDouble() * sum;
                            double acc = 0;

                            int j = 0;
                            while (acc < p) {
                                acc += items[j];
                                j++;
                            }
                            j--;
                            kSet.add(j);

                            sum -= items[j];
                            items[j] = 0;

                        }
                        return kSet;
                    }));
        }

        return user -> {
            IntSet testSet = mapSets.get(user);
            IntSet kSet = mapKSets.get(user);

            return iidx -> testSet.contains(iidx) || kSet.contains(iidx);
        };
    }

    /**
     * 
     * @param <U>
     * @param <I> 
     */
    public static class FastSamplersArgument<U, I> {

        public FastPreferenceData<U, I> trainData;
        public Map<U, IntSet> mapSets;
        public int n;

        /**
         * 
         * @param trainData
         * @param mapSets
         * @param n 
         */
        public FastSamplersArgument(FastPreferenceData<U, I> trainData, Map<U, IntSet> mapSets, int n) {
            this.trainData = trainData;
            this.mapSets = mapSets;
            this.n = n;
        }
    }

    /**
     * 
     * @param <U>
     * @param <I>
     * @param testData
     * @return 
     */
    public static <U, I> Map<U, IntSet> inTestForUser(FastPreferenceData<U, I> testData) {
        Map<U, IntSet> mapSets = testData.getAllUsers().parallel().collect(Collectors.toMap(
                user -> user,
                user -> {
                    IntSet set = new IntOpenHashSet();
                    testData.getUidxPreferences(testData.user2uidx(user))
                    .mapToInt(iv -> iv.v1)
                    .forEach(set::add);
                    return set;
                }));

        return mapSets;
    }
}
