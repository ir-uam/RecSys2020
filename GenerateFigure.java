/*
 * Copyright (C) 2019 Information Retrieval Group at Universidad Autónoma
 * de Madrid, http://ir.ii.uam.es.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package es.uam.ir.targetsampling;

import es.uam.ir.filler.Filler;
import static es.uam.ir.targetsampling.Initialize.BIASED_PATH;
import static es.uam.ir.targetsampling.Initialize.ML1M;
import static es.uam.ir.targetsampling.Initialize.ML1M_BIASED_PROPERTIES_FILE;
import static es.uam.ir.targetsampling.Initialize.RESULTS_PATH;
import static es.uam.ir.targetsampling.Initialize.UNBIASED_PATH;
import static es.uam.ir.targetsampling.Initialize.YAHOO;
import static es.uam.ir.targetsampling.Initialize.YAHOO_BIASED_PROPERTIES_FILE;
import static es.uam.ir.targetsampling.TargetSampling.EXPECTED_INTERSECTION_RATIO_FILE;
import static es.uam.ir.targetsampling.TargetSampling.TARGET_SAMPLING_FILE;
import static es.uam.ir.targetsampling.TargetSampling.TIES_AT_ZERO_FILE;
import static es.uam.ir.targetsampling.TargetSampling.TIES_FILE;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 *
 * @author Rocío Cañamares
 * @author Pablo Castells
 */
public class GenerateFigure {
    
    public final static int FULL_TARGET_SIZE_ML1M = 2000;
    public final static int FULL_TARGET_SIZE_YAHOO = 1000;
    public final static int N_FOLDS = 5;
    

    /**
     *
     * @param a
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static void main(String a[]) throws FileNotFoundException, IOException {
        int figure = Integer.valueOf(a[0]);
        //int figure = 2;
        switch (figure) {
            case 1:
                generateFigure1(
                        RESULTS_PATH + BIASED_PATH + ML1M + "-" + TARGET_SAMPLING_FILE,
                        RESULTS_PATH + "figure1.txt",
                        N_FOLDS,
                        "P@10",
                        FULL_TARGET_SIZE_ML1M);
                break;
            case 2:
                generateFigure2(
                        RESULTS_PATH + BIASED_PATH + YAHOO + "-" + TARGET_SAMPLING_FILE,
                        RESULTS_PATH + UNBIASED_PATH + YAHOO + "-" + TARGET_SAMPLING_FILE,
                        RESULTS_PATH + "figure2.txt",
                        N_FOLDS,
                        new String[]{"nDCG@10", "P@10", "Recall@10"},
                        FULL_TARGET_SIZE_YAHOO);
                break;
            case 3:
                generateFigure3(
                        RESULTS_PATH + BIASED_PATH,
                        new String[]{ML1M, YAHOO},
                        new String[]{"nDCG@10", "P@10", "Recall@10"},
                        RESULTS_PATH + "figure3.txt",
                        N_FOLDS);
                break;
            case 4:
                generateFigure4(
                        RESULTS_PATH + BIASED_PATH,
                        RESULTS_PATH + UNBIASED_PATH,
                        new String[]{ML1M, YAHOO},
                        new String[]{"nDCG@10", "P@10", "Recall@10"},
                        RESULTS_PATH + "figure4.txt",
                        N_FOLDS);
                break;
            case 5:
                generateFigure5(
                        RESULTS_PATH + BIASED_PATH,
                        new String[]{ML1M, YAHOO},
                        new String[]{"Coverage@10"},
                        RESULTS_PATH + "figure5.txt",
                        N_FOLDS);
                break;
            default:
                System.out.println("Invalid figure number");
        }
    }
    
    public static void generateFigure1(
            String inFile,
            String outFile,
            int nFolds,
            String metric,
            int fullTargetSize) throws FileNotFoundException {
        Map<String, double[]> values = new TreeMap<String, double[]>();
        Scanner in = new Scanner(new File(inFile));
        String colHeads[] = in.nextLine().split("\t");
        int colMetric = Arrays.asList(colHeads).indexOf(metric);
        while (in.hasNext()) {
            String colValues[] = in.nextLine().split("\t");
            int targetSize = Integer.valueOf(colValues[1]);
            if (targetSize != 0 && targetSize != fullTargetSize) {
                continue;
            }
            String recommender = colValues[2];
            
            double[] value = values.get(recommender);
            if (value == null) {
                value = new double[2];
                values.put(recommender, value);
            }
            int index = (targetSize == fullTargetSize) ? 0 : 1;
            value[index] += Double.valueOf(colValues[colMetric]);
        }
        
        PrintStream out = new PrintStream(outFile);
        out.println("====================");
        out.println("Dataset: ml1m");
        out.println("====================\n");
        
        out.println(metric);
        out.println("Recommender\tFull\tTest");
        for (String recommender : values.keySet()) {
            out.print(recommender);
            for (double value : values.get(recommender)) {
                out.print("\t" + value / nFolds);
            }
            out.println();
        }
    }
    
    public static void generateFigure2(
            String biasedFile,
            String unbiasedFile,
            String outFile,
            int nFolds,
            String[] metrics,
            int fullTargetSize) throws FileNotFoundException {
        List<String> metricList = Arrays.asList(metrics);
        // Metric -> -> rec system -> full, unbiased, test
        Map<String, Map<String, double[]>> values = new TreeMap<String, Map<String, double[]>>();

        //Biased file
        Scanner in = new Scanner(new File(biasedFile));
        String colHeads[] = in.nextLine().split("\t");
        for (int i = 3; i < colHeads.length; i++) {
            String metric = colHeads[i];
            if (metricList.contains(metric)) {
                values.put(metric, new TreeMap<String, double[]>());
            }
        }
        while (in.hasNext()) {
            String colValues[] = in.nextLine().split("\t");
            for (int i = 3; i < colValues.length; i++) {
                String metric = colHeads[i];
                if (!metricList.contains(metric)) {
                    continue;
                }
                int targetSize = Integer.valueOf(colValues[1]);
                if (targetSize != 0 && targetSize != fullTargetSize) {
                    continue;
                }
                String recommender = colValues[2];
                
                double[] value = values.get(metric).get(recommender);
                if (value == null) {
                    value = new double[3];
                    values.get(metric).put(recommender, value);
                }
                int index = (targetSize == fullTargetSize) ? 0 : 2;
                value[index] += Double.valueOf(colValues[i]);
            }
        }

        //Unbiased file
        in = new Scanner(new File(unbiasedFile));
        colHeads = in.nextLine().split("\t");
        while (in.hasNext()) {
            String colValues[] = in.nextLine().split("\t");
            for (int i = 3; i < colValues.length; i++) {
                String metric = colHeads[i];
                if (!metricList.contains(metric)) {
                    continue;
                }
                double[] value = values.get(metric).get(colValues[2]);
                value[1] += Double.valueOf(colValues[i]);
            }
        }
        
        PrintStream out = new PrintStream(outFile);
        out.println("====================");
        out.println("Dataset: yahoo");
        out.println("====================");
        
        for (String metric : metricList) {
            out.println("\n" + metric);
            out.println("Recommender\tFull\tUnbiased\tTest");
            for (String recommender : values.get(metric).keySet()) {
                out.print(recommender);
                for (double value : values.get(metric).get(recommender)) {
                    out.print("\t" + value / nFolds);
                }
                out.println();
            }            
        }
    }
    
    public static void generateFigure3(
            String folder,
            String datasets[],
            String metrics[],
            String outFile,
            int nFolds) throws FileNotFoundException {
        List<String> metricList = Arrays.asList(metrics);
        PrintStream out = new PrintStream(outFile);
        for (String dataset : datasets) {
            String inFile = folder + dataset + "-" + TARGET_SAMPLING_FILE;
            out.println("====================");
            out.println("Dataset: " + dataset);
            out.println("====================\n");

            // Metric -> target size -> rec system -> value
            Map<String, Map<Integer, Map<String, Double>>> values = new TreeMap<String, Map<Integer, Map<String, Double>>>();
            Set<String> recommenders = new TreeSet<String>();
            
            Scanner in = new Scanner(new File(inFile));
            String colHeads[] = in.nextLine().split("\t");
            for (int i = 3; i < colHeads.length; i++) {
                String metric = colHeads[i];
                if (metricList.contains(metric)) {
                    values.put(metric, new TreeMap<Integer, Map<String, Double>>());
                }
            }
            while (in.hasNext()) {
                String colValues[] = in.nextLine().split("\t");
                int targetSize = new Integer(colValues[1]);
                String rec = colValues[2];
                recommenders.add(rec);
                for (int i = 3; i < colValues.length; i++) {
                    String metric = colHeads[i];
                    if (!metricList.contains(metric)) {
                        continue;
                    }
                    double value = new Double(colValues[i]);
                    if (!values.get(metric).containsKey(targetSize)) {
                        values.get(metric).put(targetSize, new TreeMap<String, Double>());
                    }
                    if (!values.get(metric).get(targetSize).containsKey(rec)) {
                        values.get(metric).get(targetSize).put(rec, 0.0);
                    }
                    values.get(metric).get(targetSize).put(rec, values.get(metric).get(targetSize).get(rec) + value);
                }
            }
            
            for (String metric : metricList) {
                out.println(metric);
                out.print("Target size");
                for (String rec : recommenders) {
                    out.print("\t" + rec);
                }
                out.println();
                for (int targetSize : values.get(metric).keySet()) {
                    out.print(targetSize + "\t");
                    for (String rec : values.get(metric).get(targetSize).keySet()) {
                        out.print(values.get(metric).get(targetSize).get(rec) / nFolds + "\t");
                    }
                    out.println();
                }
                out.println();
            }
        }
        out.close();
    }
    
    public static void generateFigure4(
            String biasedFolder,
            String unbiasedFolder,
            String datasets[],
            String metrics[],
            String outFile, int nFolds) throws FileNotFoundException, IOException {
        List<String> metricList = Arrays.asList(metrics);
        PrintStream out = new PrintStream(outFile);
        for (String dataset : datasets) {
            
            out.println("====================");
            out.println("Dataset: " + dataset);
            out.println("====================\n");

            // Metric -> target size -> curve -> value
            Map<String, Map<Integer, Map<String, Double>>> values = new TreeMap<String, Map<Integer, Map<String, Double>>>();
            // Metric -> target size -> curve -> count
            Map<String, Map<Integer, Map<String, Integer>>> counts = new TreeMap<String, Map<Integer, Map<String, Integer>>>();
            Set<String> curves = new TreeSet<String>();

            //Ratio of ties & Ratio of ties at zero
            //curve -> input file
            Map<String, String> curveFiles = new HashMap<>();
            curveFiles.put("Ratio of ties", biasedFolder + dataset + "-" + TIES_FILE);
            curveFiles.put("Ratio of ties at zero", biasedFolder + dataset + "-" + TIES_AT_ZERO_FILE);
            for (String curve : curveFiles.keySet()) {
                curves.add(curve);
                String inFile = curveFiles.get(curve);
                Scanner in = new Scanner(new File(inFile));
                String colHeads[] = in.nextLine().split("\t");
                for (int i = 3; i < colHeads.length; i++) {
                    String metric = colHeads[i];
                    if (metricList.contains(metric) && !values.containsKey(metric)) {
                        values.put(metric, new TreeMap<Integer, Map<String, Double>>());
                        counts.put(metric, new TreeMap<Integer, Map<String, Integer>>());
                    }
                }
                while (in.hasNext()) {
                    String colValues[] = in.nextLine().split("\t");
                    int targetSize = new Integer(colValues[0]);
                    for (int i = 3; i < colValues.length; i++) {
                        String metric = colHeads[i];
                        if (!metricList.contains(metric)) {
                            continue;
                        }
                        double value = new Double(colValues[i]);
                        if (!values.get(metric).containsKey(targetSize)) {
                            values.get(metric).put(targetSize, new TreeMap<String, Double>());
                            counts.get(metric).put(targetSize, new TreeMap<String, Integer>());
                        }
                        if (!values.get(metric).get(targetSize).containsKey(curve)) {
                            values.get(metric).get(targetSize).put(curve, 0.0);
                            counts.get(metric).get(targetSize).put(curve, 0);
                        }
                        values.get(metric).get(targetSize).put(curve, values.get(metric).get(targetSize).get(curve) + value);
                        counts.get(metric).get(targetSize).put(curve, counts.get(metric).get(targetSize).get(curve) + 1);
                    }
                }
            }
            
            {
                //Expected intersection ratio in top n
                String curve = "Expected intersection ratio in top n";
                curves.add(curve);
                String inFile = biasedFolder + dataset + "-" + EXPECTED_INTERSECTION_RATIO_FILE;
                Scanner in = new Scanner(new File(inFile));
                in.nextLine();
                while (in.hasNext()) {
                    String colValues[] = in.nextLine().split("\t");
                    int targetSize = new Integer(colValues[1]);
                    double expectation = new Double(colValues[2]);
                    for (String metric : values.keySet()) {
                        if (!values.get(metric).get(targetSize).containsKey(curve)) {
                            values.get(metric).get(targetSize).put(curve, 0.0);
                            counts.get(metric).get(targetSize).put(curve, 0);
                        }
                        values.get(metric).get(targetSize).put(curve, values.get(metric).get(targetSize).get(curve) + expectation);
                        counts.get(metric).get(targetSize).put(curve, counts.get(metric).get(targetSize).get(curve) + 1);
                    }
                }
            }

            //Average
            for (String metric : values.keySet()) {
                for (int targetSize : values.get(metric).keySet()) {
                    for (String curve : values.get(metric).get(targetSize).keySet()) {
                        double value = values.get(metric).get(targetSize).get(curve);
                        int count = counts.get(metric).get(targetSize).get(curve);
                        values.get(metric).get(targetSize).put(curve, value / count);
                    }
                }
            }

            //Curve: Correlation with unbiased evaluation
            try {
                // Metric -> rec system -> value
                Map<String, Map<String, Double>> unbiasedValues = new TreeMap<String, Map<String, Double>>();
                
                String unbiasedFiles = unbiasedFolder + dataset + "-" + TARGET_SAMPLING_FILE;
                Scanner unbiasedIn = new Scanner(new File(unbiasedFiles));
                String unbiasedColHeads[] = unbiasedIn.nextLine().split("\t");
                for (int i = 2; i < unbiasedColHeads.length; i++) {
                    String metric = unbiasedColHeads[i];
                    if (metricList.contains(metric)) {
                        unbiasedValues.put(metric, new TreeMap<String, Double>());
                    }
                }
                while (unbiasedIn.hasNext()) {
                    String colValues[] = unbiasedIn.nextLine().split("\t");
                    String rec = colValues[2];
                    for (int i = 3; i < colValues.length; i++) {
                        String metric = unbiasedColHeads[i];
                        if (!metricList.contains(metric)) {
                            continue;
                        }
                        double value = new Double(colValues[i]);
                        if (!unbiasedValues.get(metric).containsKey(rec)) {
                            unbiasedValues.get(metric).put(rec, 0.0);
                        }
                        unbiasedValues.get(metric).put(rec, unbiasedValues.get(metric).get(rec) + value);
                    }
                }

                // Metric -> target size -> rec system -> value
                Map<String, Map<Integer, Map<String, Double>>> biasedValues = new TreeMap<String, Map<Integer, Map<String, Double>>>();
                
                String biasedFiles = biasedFolder + dataset + "-" + TARGET_SAMPLING_FILE;
                Scanner biasedIn = new Scanner(new File(biasedFiles));
                String biasedColHeads[] = biasedIn.nextLine().split("\t");
                for (int i = 2; i < biasedColHeads.length; i++) {
                    String metric = biasedColHeads[i];
                    if (metricList.contains(metric)) {
                        biasedValues.put(metric, new TreeMap<Integer, Map<String, Double>>());
                    }
                }
                while (biasedIn.hasNext()) {
                    String colValues[] = biasedIn.nextLine().split("\t");
                    int targetSize = new Integer(colValues[1]);
                    String rec = colValues[2];
                    for (int i = 3; i < colValues.length; i++) {
                        String metric = biasedColHeads[i];
                        if (!metricList.contains(metric)) {
                            continue;
                        }
                        double value = new Double(colValues[i]);
                        if (!biasedValues.get(metric).containsKey(targetSize)) {
                            biasedValues.get(metric).put(targetSize, new TreeMap<String, Double>());
                        }
                        if (!biasedValues.get(metric).get(targetSize).containsKey(rec)) {
                            biasedValues.get(metric).get(targetSize).put(rec, 0.0);
                        }
                        biasedValues.get(metric).get(targetSize).put(rec, biasedValues.get(metric).get(targetSize).get(rec) + value / nFolds);
                    }
                }

                //Compute kendal correlation
                String curve = "Correlation with unbiased evaluation";
                curves.add(curve);
                for (String metric : values.keySet()) {
                    for (int targetSize : values.get(metric).keySet()) {
                        double kendalCorrelation = 0.0;
                        List<String> recommenders = new ArrayList<>(biasedValues.get(metric).get(targetSize).keySet());
                        int n = recommenders.size();
                        for (int i = 0; i < n; i++) {
                            String rec1 = recommenders.get(i);
                            double biasedValue1 = biasedValues.get(metric).get(targetSize).get(rec1);
                            double unbiasedValue1 = unbiasedValues.get(metric).get(rec1);
                            for (int j = i + 1; j < n; j++) {
                                String rec2 = recommenders.get(j);
                                double biasedValue2 = biasedValues.get(metric).get(targetSize).get(rec2);
                                double unbiasedValue2 = unbiasedValues.get(metric).get(rec2);
                                double diffBiased = biasedValue2 - biasedValue1;
                                double diffUnBiased = unbiasedValue2 - unbiasedValue1;
                                kendalCorrelation += Math.signum(diffBiased*diffUnBiased);
                            }
                        }
                        kendalCorrelation *= 2 * 1.0 / (n * (n - 1));
                        values.get(metric).get(targetSize).put(curve, kendalCorrelation);
                    }
                }
                
            } catch (FileNotFoundException e) {
                //Do nothing: there is not unbiased data, as is the case for MovieLens 1M
            }
            
            {
                //Sum of p-values
                //Yahoo
                {
                    Configuration conf = new Configuration(YAHOO_BIASED_PROPERTIES_FILE);
                    conf.setAllRecs(true);
                    conf.setResultsPath(conf.getResultsPath() + "allrecs-");
                    TargetSampling targetSelection = new TargetSampling(conf);
                    targetSelection.runCrossValidation();
                }

                //MovieLens
                {
                    Configuration conf = new Configuration(ML1M_BIASED_PROPERTIES_FILE);
                    conf.setAllRecs(true);
                    conf.setResultsPath(conf.getResultsPath() + "allrecs-");
                    TargetSampling targetSelection = new TargetSampling(conf);
                    targetSelection.runCrossValidation();
                }

                String curve = "Sum of p-values";
                curves.add(curve);
                String inFile = biasedFolder + dataset + "-allrecs-pvalues.txt";
                Scanner in = new Scanner(new File(inFile));
                String colHeads[] = in.nextLine().split("\t");
                while (in.hasNext()) {
                    String colValues[] = in.nextLine().split("\t");
                    int targetSize = new Integer(colValues[0]);
                    for (int i = 3; i < colValues.length; i++) {
                        String metric = colHeads[i];
                        if (!metricList.contains(metric)) {
                            continue;
                        }
                        double value = new Double(colValues[i]);
                        if (!values.get(metric).get(targetSize).containsKey(curve)) {
                            values.get(metric).get(targetSize).put(curve, 0.0);
                            counts.get(metric).get(targetSize).put(curve, 0);
                        }
                        values.get(metric).get(targetSize).put(curve, values.get(metric).get(targetSize).get(curve) + value);
                    }
                }
            }
            
            for (String metric: metricList) {
                out.println(metric);
                out.print("Target size");
                for (String curve : curves) {
                    out.print("\t" + curve);
                }
                out.println();
                for (int targetSize : values.get(metric).keySet()) {
                    out.print(targetSize + "\t");
                    for (String curve : values.get(metric).get(targetSize).keySet()) {
                        out.print(values.get(metric).get(targetSize).get(curve) + "\t");
                    }
                    out.println();
                }
                out.println();
            }
        }
        out.close();
    }
    
    public static void generateFigure5(
            String folder,
            String datasets[],
            String metrics[],
            String outFile,
            int nFolds) throws FileNotFoundException, IOException {
        //Yahoo
        {
            Configuration conf = new Configuration(YAHOO_BIASED_PROPERTIES_FILE);
            conf.setFillMode(Filler.Mode.NONE);
            conf.setResultsPath(conf.getResultsPath() + "nofill-");
            TargetSampling targetSelection = new TargetSampling(conf);
            targetSelection.runCrossValidation();
        }

        //MovieLens
        {
            Configuration conf = new Configuration(ML1M_BIASED_PROPERTIES_FILE);
            conf.setFillMode(Filler.Mode.NONE);
            conf.setResultsPath(conf.getResultsPath() + "nofill-");
            TargetSampling targetSelection = new TargetSampling(conf);
            targetSelection.runCrossValidation();
        }
        
        for (int i = 0; i < datasets.length; i++) {
            datasets[i] += "-nofill";
        }
        
        generateFigure3(folder, datasets, metrics, outFile, nFolds);
    }
    
}
