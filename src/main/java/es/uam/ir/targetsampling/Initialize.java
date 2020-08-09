/*
* Copyright (C) 2020 Information Retrieval Group at Universidad Autónoma
* de Madrid, http://ir.ii.uam.es.
*
* This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package es.uam.ir.targetsampling;

import es.uam.ir.crossvalidation.CrossValidation;
import es.uam.ir.util.Timer;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.LogManager;

/**
 *
 * @author Rocío Cañamares
 * @author Pablo Castells
 */
public class Initialize {

    public final static String ML1M_PATH = "datasets/ml1m/";
    public final static String ORIGINAL_ML1M_DATASET_PATH = ML1M_PATH + "ratings.dat";
    public final static String PREPROCESSED_ML1M_DATASET_PATH = ML1M_PATH + "data.txt";

    public final static String YAHOO_PATH = "datasets/yahoo/";
    public final static String ORIGINAL_YAHOO_TRAIN_DATASET_PATH = YAHOO_PATH + "ydata-ymusic-rating-study-v1_0-train.txt";
    public final static String ORIGINAL_YAHOO_TEST_DATASET_PATH = YAHOO_PATH + "ydata-ymusic-rating-study-v1_0-test.txt";
    public final static String PREPROCESSED_YAHOO_TRAIN_DATASET_PATH = YAHOO_PATH + "data.txt";
    public final static String PREPROCESSED_YAHOO_TEST_DATASET_PATH = YAHOO_PATH + "unbiased-test.txt";

    public final static String YAHOO_BIASED_PROPERTIES_FILE = "conf/yahoo-biased.properties";
    public final static String YAHOO_UNBIASED_PROPERTIES_FILE = "conf/yahoo-unbiased.properties";
    public final static String ML1M_BIASED_PROPERTIES_FILE = "conf/movielens-biased.properties";

    /**
     *
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        LogManager.getLogManager().reset();

        Timer.start("Processing Movielens 1M...");
        processMl1m();
        Timer.done("");
        
        Timer.start("Processing Yahoo R3...");
        processYahoo();
        Timer.done("");

        //Yahoo
        {
            Configuration conf = new Configuration(YAHOO_BIASED_PROPERTIES_FILE);
            TargetSampling targetSelection = new TargetSampling(conf);
            targetSelection.runCrossValidation();
        }
        {
            Configuration conf = new Configuration(YAHOO_UNBIASED_PROPERTIES_FILE);
            TargetSampling targetSelection = new TargetSampling(conf);
            targetSelection.runWithUnbiasedTest(conf.getTestPath());
        }

        //MovieLens
        {
            Configuration conf = new Configuration(ML1M_BIASED_PROPERTIES_FILE);
            TargetSampling targetSelection = new TargetSampling(conf);
            targetSelection.runCrossValidation();
        }

    }

    static void processMl1m() throws IOException {
        RandomAccessFile ml1mIn = new RandomAccessFile(ORIGINAL_ML1M_DATASET_PATH, "r");
        byte bytes[] = new byte[(int) ml1mIn.length()];
        ml1mIn.read(bytes);
        String ratings = new String(bytes, StandardCharsets.UTF_8);

        PrintStream ml1mOut = new PrintStream(PREPROCESSED_ML1M_DATASET_PATH);
        ml1mOut.print(ratings.replace("::", "\t"));
        ml1mOut.close();
        
        CrossValidation.crowssValidation(PREPROCESSED_ML1M_DATASET_PATH, ML1M_PATH, GenerateFigure.N_FOLDS);
    }

    static void processYahoo() throws FileNotFoundException, IOException {
        // No format change neded
        Files.copy(
                Paths.get(ORIGINAL_YAHOO_TEST_DATASET_PATH),
                Paths.get(PREPROCESSED_YAHOO_TEST_DATASET_PATH),
                StandardCopyOption.REPLACE_EXISTING);

        Set<Long> testUsers = new HashSet<Long>();
        Scanner scn = new Scanner(new File(PREPROCESSED_YAHOO_TEST_DATASET_PATH));
        while (scn.hasNext()) {
            testUsers.add(new Long(scn.nextLine().split("\t")[0]));
        }

        PrintStream trainOut = new PrintStream(PREPROCESSED_YAHOO_TRAIN_DATASET_PATH);
        scn = new Scanner(new File(ORIGINAL_YAHOO_TRAIN_DATASET_PATH));
        while (scn.hasNext()) {
            String rating = scn.nextLine();
            if (testUsers.contains(new Long(rating.split("\t")[0]))) {
                trainOut.println(rating);
            }
        }
        trainOut.close();
        
        CrossValidation.crowssValidation(PREPROCESSED_YAHOO_TRAIN_DATASET_PATH, YAHOO_PATH, GenerateFigure.N_FOLDS);
    }

}
