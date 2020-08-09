/*
 * Copyright (C) 2020 Information Retrieval Group at Universidad Autónoma
 * de Madrid, http://ir.ii.uam.es.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package es.uam.ir.targetsampling;

import es.uam.ir.filler.Filler;
import es.uam.eps.ir.ranksys.core.Recommendation;
import es.uam.eps.ir.ranksys.fast.FastRecommendation;
import es.uam.eps.ir.ranksys.fast.index.FastItemIndex;
import es.uam.eps.ir.ranksys.fast.index.FastUserIndex;
import es.uam.eps.ir.ranksys.fast.index.SimpleFastItemIndex;
import es.uam.eps.ir.ranksys.fast.index.SimpleFastUserIndex;
import es.uam.eps.ir.ranksys.fast.preference.FastPreferenceData;
import es.uam.eps.ir.ranksys.fast.preference.SimpleFastPreferenceData;
import es.uam.eps.ir.ranksys.metrics.AbstractRecommendationMetric;
import es.uam.ir.ranksys.metrics.basic.Coverage;
import es.uam.ir.ranksys.metrics.basic.NDCG;
import es.uam.eps.ir.ranksys.metrics.basic.Precision;
import es.uam.eps.ir.ranksys.metrics.basic.Recall;
import es.uam.eps.ir.ranksys.metrics.rel.BinaryRelevanceModel;
import es.uam.eps.ir.ranksys.mf.als.HKVFactorizer;
import es.uam.eps.ir.ranksys.mf.rec.MFRecommender;
import es.uam.eps.ir.ranksys.nn.user.UserNeighborhoodRecommender;
import es.uam.eps.ir.ranksys.nn.user.neighborhood.TopKUserNeighborhood;
import es.uam.eps.ir.ranksys.nn.user.sim.UserSimilarity;
import es.uam.eps.ir.ranksys.nn.user.sim.VectorCosineUserSimilarity;
import es.uam.eps.ir.ranksys.rec.Recommender;
import es.uam.eps.ir.ranksys.rec.fast.FastRecommender;
import es.uam.eps.ir.ranksys.rec.fast.basic.PopularityRecommender;
import es.uam.eps.ir.ranksys.rec.runner.fast.FastFilters;
import es.uam.ir.datagenerator.TruncateRatings;
import es.uam.ir.util.Timer;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.stream.DoubleStream;
import org.apache.commons.math3.stat.inference.TTest;
import org.ranksys.formats.index.ItemsReader;
import org.ranksys.formats.index.UsersReader;
import static org.ranksys.formats.parsing.Parsers.lp;
import org.ranksys.formats.preference.SimpleRatingPreferencesReader;
import es.uam.ir.ranksys.rec.runner.fast.FastSamplers;
import es.uam.ir.crossvalidation.CrossValidation;
import es.uam.ir.filler.Filler.Mode;
import es.uam.ir.util.GetUsersAndItems;
import java.io.ByteArrayInputStream;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.LogManager;
import java.util.stream.Collectors;
import org.ranksys.core.util.tuples.Tuple2id;
import org.ranksys.core.util.tuples.Tuple2od;
import es.uam.ir.ranksys.rec.fast.basic.RandomRecommender;
import es.uam.ir.ranksys.rec.fast.basic.AverageRatingRecommender;
import es.uam.ir.ranksys.nn.user.NormUserNeighborhoodRecommenderWithMinimum;
import java.io.FileNotFoundException;

/**
 *
 * @author Rocío Cañamares
 * @author Pablo Castells
 */
public class TargetSampling {

    private final Configuration conf;
    public final static String TARGET_SAMPLING_FILE = "target-sampling.txt";
    public final static String P_VALUES_FILE = "pvalues.txt";
    public final static String TIES_FILE = "ties.txt";
    public final static String TIES_AT_ZERO_FILE = "tiesAtZero.txt";
    public final static String EXPECTED_INTERSECTION_RATIO_FILE = "expected-intersection-ratio.txt";

    private final String[] METRIC_NAMES = new String[]{
        "Coverage",
        "nDCG",
        "P",
        "Recall",};

    /**
     *
     * @param conf
     */
    public TargetSampling(
            Configuration conf) {
        this.conf = conf;
    }

    /**
     *
     * @throws IOException
     */
    public void runCrossValidation() throws IOException {
        Timer.start("Starting...");

        for (int i = 0; i < METRIC_NAMES.length; i++) {
            METRIC_NAMES[i] += "@" + conf.getCutoff();
        }

        LogManager.getLogManager().reset();

        // Read files
        Timer.start("Reading files...");
        ByteArrayInputStream[] usersAndItemsInputStreams = GetUsersAndItems.run(conf.getDataPath() + "data.txt");
        FastUserIndex<Long> userIndex = SimpleFastUserIndex.load(UsersReader.read(usersAndItemsInputStreams[0], lp));
        FastItemIndex<Long> itemIndex = SimpleFastItemIndex.load(ItemsReader.read(usersAndItemsInputStreams[1], lp));

        Timer.done("");

        Map<String, Map<String, double[]>> evalsPerUser = new HashMap<>();
        int nUsersInCrossValidation = 0;
        try (PrintStream out = new PrintStream(conf.getResultsPath() + TARGET_SAMPLING_FILE);
                PrintStream outExpectation = new PrintStream(conf.getResultsPath() + EXPECTED_INTERSECTION_RATIO_FILE)) {
            //Header
            outExpectation.println("fold\ttarget size\texpected intersection ratio in top n");
            out.print("fold\ttarget size\trecommender system");
            for (String metric : METRIC_NAMES) {
                out.print("\t" + metric);
            }
            out.println();

            //Run
            for (int n : conf.getTargetSizes()) {
                int targetSize = n;
                nUsersInCrossValidation = 0;
                for (int currentFold = 1; currentFold <= conf.getNFolds(); currentFold++) {
                    System.out.println("Running fold " + currentFold);
                    FastPreferenceData<Long, Long> trainData = SimpleFastPreferenceData.load(SimpleRatingPreferencesReader.get().read(conf.getDataPath() + currentFold + "-data-train.txt", lp, lp), userIndex, itemIndex);
                    FastPreferenceData<Long, Long> testData = SimpleFastPreferenceData.load(SimpleRatingPreferencesReader.get().read(conf.getDataPath() + currentFold + "-data-test.txt", lp, lp), userIndex, itemIndex);
                    FastPreferenceData<Long, Long> positiveTrainData = TruncateRatings.run(trainData, conf.getThreshold());

                    //Sampler:
                    Function<Long, IntPredicate> sampler = FastSamplers.uniform(trainData, FastSamplers.inTestForUser(testData), targetSize);
                    Function<Long, IntPredicate> notTrainFilter = FastFilters.notInTrain(trainData);
                    Function<Long, IntPredicate> userFilter = FastFilters.and(sampler, notTrainFilter);

                    runSplit(userIndex,
                            itemIndex,
                            nUsersInCrossValidation,
                            targetSize,
                            currentFold,
                            trainData,
                            positiveTrainData,
                            testData,
                            evalsPerUser,
                            userFilter,
                            out
                    );

                    nUsersInCrossValidation += trainData.getUsersWithPreferences().count();
                    double expectation = trainData.getUsersWithPreferences()
                            .mapToDouble(user -> {
                                long nu = itemIndex.getAllIidx().filter(item -> userFilter.apply(user).test(item)).count();
                                if (nu == 0) {
                                    return 1;
                                }
                                long k = Math.min(nu, 10);
                                return k * 1.0 / nu;
                            }).filter(v -> !Double.isInfinite(v) && !Double.isNaN(v)).sum() * 1.0 / trainData.numUsers();
                    outExpectation.println(currentFold + "\t" + targetSize + "\t" + expectation);
                }
            }
        }
        processEvals(evalsPerUser, conf.getResultsPath(), nUsersInCrossValidation);
    }

    /**
     *
     * @param testPath
     * @throws IOException
     */
    public void runWithUnbiasedTest(String testPath) throws IOException {
        Timer.start("Starting...");

        for (int i = 0; i < METRIC_NAMES.length; i++) {
            METRIC_NAMES[i] += "@" + conf.getCutoff();
        }

        LogManager.getLogManager().reset();

        // Read files
        Timer.start("Reading files...");
        ByteArrayInputStream[] usersAndItemsInputStreams = GetUsersAndItems.run(conf.getDataPath() + "data.txt");
        FastUserIndex<Long> userIndex = SimpleFastUserIndex.load(UsersReader.read(usersAndItemsInputStreams[0], lp));
        FastItemIndex<Long> itemIndex = SimpleFastItemIndex.load(ItemsReader.read(usersAndItemsInputStreams[1], lp));
        FastPreferenceData<Long, Long> testData = SimpleFastPreferenceData.load(SimpleRatingPreferencesReader.get().read(testPath, lp, lp), userIndex, itemIndex);
        Timer.done("");

        Map<String, Map<String, double[]>> evalsPerUser = new HashMap<>();
        int nUsersInCrossValidation = 0;
        try (PrintStream out = new PrintStream(conf.getResultsPath() + TARGET_SAMPLING_FILE);
                PrintStream outExpectation = new PrintStream(conf.getResultsPath() + EXPECTED_INTERSECTION_RATIO_FILE)) {
            //Header
            outExpectation.println("fold\ttarget size\texpected intersection ratio in top n");
            out.print("fold\ttarget size\trec");
            for (String metric : METRIC_NAMES) {
                out.print("\t" + metric);
            }
            out.println();

            //Run
            for (int n : conf.getTargetSizes()) {
                int targetSize = n;
                nUsersInCrossValidation = 0;

                for (int currentFold = 1; currentFold <= conf.getNFolds(); currentFold++) {
                    System.out.println("Running fold " + currentFold);
                    FastPreferenceData<Long, Long> trainData = SimpleFastPreferenceData.load(SimpleRatingPreferencesReader.get().read(conf.getDataPath() + currentFold + "-data-train.txt", lp, lp), userIndex, itemIndex);
                    FastPreferenceData<Long, Long> positiveTrainData = TruncateRatings.run(trainData, conf.getThreshold());
                    //Sampler:
                    Function<Long, IntPredicate> sampler = FastSamplers.uniform(trainData, FastSamplers.inTestForUser(testData), targetSize);
                    Function<Long, IntPredicate> notTrainFilter = FastFilters.notInTrain(trainData);
                    Function<Long, IntPredicate> userFilter = FastFilters.and(sampler, notTrainFilter);

                    runSplit(userIndex, itemIndex, nUsersInCrossValidation, targetSize, currentFold, trainData, positiveTrainData, testData, evalsPerUser, userFilter, out);
                    Timer.done("...");

                    nUsersInCrossValidation += trainData.getUsersWithPreferences().count();
                    double expectation = trainData.getUsersWithPreferences()
                            .mapToDouble(user -> {
                                long nu = itemIndex.getAllIidx().filter(item -> userFilter.apply(user).test(item)).count();
                                if (nu == 0) {
                                    return 1;
                                }
                                long k = Math.min(nu, 10);
                                return k * 1.0 / nu;
                            }).filter(v -> !Double.isInfinite(v) && !Double.isNaN(v)).sum() * 1.0 / trainData.numUsers();
                    outExpectation.println(targetSize + "\t" + expectation);
                }
            }
        }
        processEvals(evalsPerUser, conf.getResultsPath(), nUsersInCrossValidation);
    }

    private void runSplit(
            FastUserIndex<Long> userIndex,
            FastItemIndex<Long> itemIndex,
            int nUsersInCrossValidation,
            int targetSize,
            int currentFold,
            FastPreferenceData<Long, Long> trainData,
            FastPreferenceData<Long, Long> positiveTrainData,
            FastPreferenceData<Long, Long> testData,
            Map<String, Map<String, double[]>> evalsPerUser,
            Function<Long, IntPredicate> userFilter,
            PrintStream out) throws IOException {

        Set<Long> trainUsers = trainData.getUsersWithPreferences().collect(Collectors.toSet());
        Set<Long> targetUsers = trainUsers;
        Filler<Long, Long> filler = new Filler<>(conf.getFillMode(), itemIndex, userIndex, trainData);

        /////////////
        // METRICS //
        /////////////
        Map<String, AbstractRecommendationMetric<Long, Long>> metrics = new HashMap<>();

        int cutoff = conf.getCutoff();
        int threshold = conf.getThreshold();
        BinaryRelevanceModel<Long, Long> binRel = new BinaryRelevanceModel<>(false, testData, threshold);
        NDCG.NDCGRelevanceModel ndcgModel = new NDCG.NDCGRelevanceModel<>(false, testData, threshold);
        metrics.put("P@" + cutoff, new Precision<>(cutoff, binRel));
        metrics.put("nDCG@" + cutoff, new NDCG<>(cutoff, ndcgModel));
        metrics.put("Recall@" + cutoff, new Recall<>(cutoff, binRel));
        metrics.put("Coverage@" + cutoff, new Coverage<>(cutoff));

        ////////////////////////////////////////////////
        // GENERATING RECOMMENDATIONS AND EVALUATIONS //
        ////////////////////////////////////////////////
        Map<String, Supplier<Recommender<Long, Long>>> recMap = new HashMap<>();
        recMap.put("Random", () -> new RandomRecommender<>(userIndex, itemIndex));
        recMap.put("Popularity", () -> new PopularityRecommender<>(trainData));
        recMap.put("Average Rating", () -> new AverageRatingRecommender<>(trainData, threshold));

        if (conf.isAllRecs()) {
            recMap.putAll(getAllRecs(userIndex, itemIndex, trainData, positiveTrainData));
        } else {
            recMap.putAll(getFullAndTestRecs(userIndex, itemIndex, trainData, positiveTrainData));
        }

        eval(
                userIndex,
                itemIndex,
                nUsersInCrossValidation,
                targetSize,
                currentFold,
                targetUsers,
                userFilter,
                recMap,
                metrics,
                evalsPerUser,
                out,
                filler);
    }

    private void eval(
            FastUserIndex<Long> userIndex,
            FastItemIndex<Long> itemIndex,
            int nUsersInCrossValidation,
            int targetSize,
            int currentFold,
            Set<Long> targetUsers,
            Function<Long, IntPredicate> userFilter,
            Map<String, Supplier<Recommender<Long, Long>>> recMap,
            Map<String, AbstractRecommendationMetric<Long, Long>> metrics,
            Map<String, Map<String, double[]>> evalsPerUser,
            PrintStream out,
            Filler<Long, Long> filler) {

        int m = userIndex.numUsers();
        int mTrain = targetUsers.size();
        recMap.keySet().stream().forEachOrdered(recNameAux -> {
            String recName = targetSize + "\t" + recNameAux;
            if (!evalsPerUser.containsKey(recName)) {
                Map<String, double[]> values = new HashMap<>();
                for (String metric : METRIC_NAMES) {
                    values.put(metric, new double[m * conf.getNFolds()]);
                }
                evalsPerUser.put(recName, values);
            }

            System.out.print("Running " + recName);
            FastRecommender<Long, Long> recommendation = (FastRecommender<Long, Long>) recMap.get(recNameAux).get();
            Function<Long, Recommendation<Long, Long>> recProvider = user -> {
                FastRecommendation rec = recommendation.getRecommendation(userIndex.user2uidx(user), conf.getCutoff(), userFilter.apply(user));
                return new Recommendation<>(userIndex.uidx2user(rec.getUidx()), rec.getIidxs().stream().map(iv -> new Tuple2od<>(itemIndex.iidx2item(iv.v1), iv.v2)).collect(Collectors.toList()));
            };

            Map<String, double[]> actualValues = new HashMap<>();
            for (String metric : METRIC_NAMES) {
                actualValues.put(metric, new double[m]);
            }

            targetUsers.stream().parallel()
                    .map(user -> recProvider.apply(user))
                    .map(rec -> {
                        List<Tuple2id> items = rec.getItems()
                        .stream()
                        .map(ip -> new Tuple2id(itemIndex.item2iidx(ip.v1), ip.v2))
                        .collect(Collectors.toList());
                        List<Tuple2od<Long>> newItems = filler
                        .fill(items, conf.getCutoff(), userFilter.apply(rec.getUser()), rec.getUser())
                        .stream()
                        .map(ip -> new Tuple2od<>(itemIndex.iidx2item(ip.v1), ip.v2))
                        .collect(Collectors.toList());
                        return new Recommendation<>(rec.getUser(), newItems);
                    }).forEachOrdered(rec -> {
                        metrics.forEach((metricName, metric) -> {
                            actualValues.get(metricName)[userIndex.user2uidx(rec.getUser())] = metric.evaluate(rec);
                        });
                    });

            Map<String, double[]> pastValues = evalsPerUser.get(recName);

            int i = 0;
            for (Long user : targetUsers) {
                int u = userIndex.user2uidx(user);
                for (String metricName : METRIC_NAMES) {
                    double value = actualValues.get(metricName)[u];
                    pastValues.get(metricName)[nUsersInCrossValidation + i] = value;
                }
                i++;
            }

            //Values
            out.print(currentFold);
            out.print("\t");
            out.print(recName);
            for (String metricName : METRIC_NAMES) {
                out.print("\t" + DoubleStream.of(actualValues.get(metricName)).sum() / mTrain);
            }
            out.println();

            Timer.done("   done");
        });

    }

    private void processEvals(Map<String, Map<String, double[]>> evalsPerUser, String resultsPath, int nUsersInCrossValidation) throws FileNotFoundException {
        try (
                PrintStream outPvalues = new PrintStream(resultsPath + P_VALUES_FILE);
                PrintStream outTiesAtZero = new PrintStream(resultsPath + TIES_AT_ZERO_FILE);
                PrintStream outTies = new PrintStream(resultsPath + TIES_FILE)) {
            //Header
            outPvalues.print("target size\t");
            outPvalues.print("recommender system 1\trecommender system 2");
            for (String metric : METRIC_NAMES) {
                outPvalues.print("\t" + metric);
            }
            outPvalues.println();

            outTies.print("target size\t");
            outTies.print("recommender system 1\trecommender system 2");
            for (String metric : METRIC_NAMES) {
                outTies.print("\t" + metric);
            }
            outTies.println();

            outTiesAtZero.print("target size\t");
            outTiesAtZero.print("recommender system 1\trecommender system 2");
            for (String metric : METRIC_NAMES) {
                outTiesAtZero.print("\t" + metric);
            }
            outTiesAtZero.println();

            List<String> recNames = new ArrayList<>(evalsPerUser.keySet());
            TTest ttest = new TTest();

            for (String recName : recNames) {
                Map<String, double[]> values = evalsPerUser.get(recName);
                for (String metric : METRIC_NAMES) {
                    double[] oldValues = values.get(metric);
                    values.put(metric, Arrays.copyOf(oldValues, nUsersInCrossValidation));
                }
            }

            for (int i = 0; i < recNames.size(); i++) {
                String rec1 = recNames.get(i);
                String rec1Name = rec1.split("\t")[1];
                int n1 = Integer.valueOf(rec1.split("\t")[0]);
                Map<String, double[]> values1 = evalsPerUser.get(rec1);

                for (int j = i + 1; j < recNames.size(); j++) {
                    String rec2 = recNames.get(j);
                    String rec2Name = rec2.split("\t")[1];
                    int n2 = Integer.valueOf(rec2.split("\t")[0]);
                    if (n1 != n2) {
                        continue;
                    }
                    outPvalues.print(n1 + "\t");
                    outTies.print(n1 + "\t");
                    outTiesAtZero.print(n1 + "\t");

                    Map<String, double[]> values2 = evalsPerUser.get(rec2);
                    outPvalues.print(rec1Name + "\t" + rec2Name);
                    outTies.print(rec1Name + "\t" + rec2Name);
                    outTiesAtZero.print(rec1Name + "\t" + rec2Name);
                    for (String metric : METRIC_NAMES) {
                        double p_value = ttest.pairedTTest(values1.get(metric), values2.get(metric));
                        double nTies = nTies(values1.get(metric), values2.get(metric));
                        double nTiesAtZero = nTiesAtZero(values1.get(metric), values2.get(metric));
                        outPvalues.print("\t" + p_value);
                        outTies.print("\t" + nTies * 1.0 / nUsersInCrossValidation);
                        outTiesAtZero.print("\t" + nTiesAtZero * 1.0 / nUsersInCrossValidation);
                    }
                    outPvalues.println();
                    outTies.println();
                    outTiesAtZero.println();
                }
            }
        }
    }

    private Map<String, Supplier<Recommender<Long, Long>>> getAllRecs(
            FastUserIndex<Long> userIndex,
            FastItemIndex<Long> itemIndex,
            FastPreferenceData<Long, Long> trainData,
            FastPreferenceData<Long, Long> positiveTrainData) {
        Map<String, Supplier<Recommender<Long, Long>>> recMap = new HashMap<>();

        UserSimilarity<Long> sim = new VectorCosineUserSimilarity<>(trainData, 0.5, true);
        for (int k : conf.getKnnParamK()) {
            recMap.put("kNN (k=" + k + ")", () -> new UserNeighborhoodRecommender<>(positiveTrainData, new TopKUserNeighborhood<>(sim, k), 1));
        }

        for (int k : conf.getNormKnnParamK()) {
            for (int min : conf.getNormKnnParamMin()) {
                recMap.put("Normalized kNN (k=" + k + ", min=" + min + ")", () -> new NormUserNeighborhoodRecommenderWithMinimum<>(
                        positiveTrainData,
                        new TopKUserNeighborhood<>(sim, k), 1, min));
            }
        }

        int numIter = 20;
        for (int k : conf.getImfParamK()) {
            for (double lambda : conf.getImfParamLambda()) {
                for (double alpha : conf.getImfParamAlpha()) {
                    recMap.put("iMF (k=" + k + ", lambda=" + lambda + ", alpha=" + alpha + ")", () -> new MFRecommender<>(userIndex, itemIndex,
                            new HKVFactorizer<Long, Long>(lambda, (double x) -> 1 + alpha * x, numIter).factorize(k, trainData)));
                }
            }
        }

        return recMap;
    }

    private Map<String, Supplier<Recommender<Long, Long>>> getFullAndTestRecs(
            FastUserIndex<Long> userIndex,
            FastItemIndex<Long> itemIndex,
            FastPreferenceData<Long, Long> trainData,
            FastPreferenceData<Long, Long> positiveTrainData) {
        Map<String, Supplier<Recommender<Long, Long>>> recMap = new HashMap<>();

        UserSimilarity<Long> sim = new VectorCosineUserSimilarity<>(trainData, 0.5, true);
        if (conf.getKnnFullParamK() == conf.getKnnTestParamK()) {
            recMap.put("kNN (full/test)", () -> new UserNeighborhoodRecommender<>(positiveTrainData, new TopKUserNeighborhood<>(sim, conf.getKnnFullParamK()), 1));
        } else {
            recMap.put("kNN (full)", () -> new UserNeighborhoodRecommender<>(positiveTrainData, new TopKUserNeighborhood<>(sim, conf.getKnnFullParamK()), 1));
            recMap.put("kNN (test)", () -> new UserNeighborhoodRecommender<>(positiveTrainData, new TopKUserNeighborhood<>(sim, conf.getKnnTestParamK()), 1));
        }

        if (conf.getNormKnnFullParamK() == conf.getNormKnnTestParamK()
                && conf.getNormKnnFullParamMin() == conf.getNormKnnTestParamMin()) {
            recMap.put("Normalized kNN (full/test)", () -> new NormUserNeighborhoodRecommenderWithMinimum<>(
                    positiveTrainData,
                    new TopKUserNeighborhood<>(sim, conf.getNormKnnFullParamK()), 1, conf.getNormKnnFullParamMin()));
        } else {
            recMap.put("Normalized kNN (full)", () -> new NormUserNeighborhoodRecommenderWithMinimum<>(
                    positiveTrainData,
                    new TopKUserNeighborhood<>(sim, conf.getNormKnnFullParamK()), 1, conf.getNormKnnFullParamMin()));
            recMap.put("Normalized kNN (test)", () -> new NormUserNeighborhoodRecommenderWithMinimum<>(
                    positiveTrainData,
                    new TopKUserNeighborhood<>(sim, conf.getNormKnnTestParamK()), 1, conf.getNormKnnTestParamMin()));
        }

        int numIter = 20;
        if (conf.getImfFullParamK() == conf.getImfTestParamK()
                && conf.getImfFullParamLambda() == conf.getImfTestParamLambda()
                && conf.getImfFullParamAlpha() == conf.getImfTestParamAlpha()) {
            recMap.put("iMF (full/test)", () -> new MFRecommender<>(userIndex, itemIndex,
                    new HKVFactorizer<Long, Long>(conf.getImfFullParamLambda(),
                            (double x) -> 1 + conf.getImfFullParamAlpha() * x, numIter).factorize(
                            conf.getImfFullParamK(), trainData)));
        } else {
            recMap.put("iMF (full)", () -> new MFRecommender<>(userIndex, itemIndex,
                    new HKVFactorizer<Long, Long>(conf.getImfFullParamLambda(),
                            (double x) -> 1 + conf.getImfFullParamAlpha() * x, numIter).factorize(
                            conf.getImfFullParamK(), trainData)));
            recMap.put("iMF (test)", () -> new MFRecommender<>(userIndex, itemIndex,
                    new HKVFactorizer<Long, Long>(conf.getImfTestParamLambda(),
                            (double x) -> 1 + conf.getImfTestParamAlpha() * x, numIter).factorize(
                            conf.getImfTestParamK(), trainData)));
        }
        return recMap;
    }

    private static int nTies(double[] v1, double[] v2) {
        int nTies = 0;
        for (int i = 0; i < v1.length; i++) {
            if (Double.compare(v1[i], v2[i]) == 0) {
                nTies++;
            }
        }
        return nTies;
    }

    private static int nTiesAtZero(double[] v1, double[] v2) {
        int nTiesAtZero = 0;
        for (int i = 0; i < v1.length; i++) {
            if (Double.compare(v1[i], v2[i]) == 0 && Double.compare(v1[i], 0.0) == 0) {
                nTiesAtZero++;
            }
        }
        return nTiesAtZero;
    }

}
