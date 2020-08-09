/*
* Copyright (C) 2020 Information Retrieval Group at Universidad Autónoma
* de Madrid, http://ir.ii.uam.es.
*
* This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/
package es.uam.ir.crossvalidation;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Random;

/**
 *
 * @author Rocío Cañamares
 * @author Pablo Castells
 */
public class CrossValidation {

    private static final Random rnd = new Random();

    /**
     * 
     * @param dataPath
     * @param outputPath
     * @param nfolds
     * @throws IOException 
     */
    public static void crowssValidation(String dataPath, String outputPath, int nfolds) throws IOException {
        PrintStream trainData[] = new PrintStream[nfolds];
        PrintStream testData[] = new PrintStream[nfolds];

        for (int i = 0; i < nfolds; i++) {
            trainData[i] = new PrintStream(outputPath + (i+1) + "-data-train.txt");
            testData[i] = new PrintStream(outputPath + (i+1) + "-data-test.txt");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(dataPath)))) {
            reader.lines().forEach(l -> {
                int fold = rnd.nextInt(nfolds);
                testData[fold].println(l);
                for (int i = 0; i < nfolds; i++) {
                    if (i == fold) continue;
                    trainData[i].println(l);
                }
            });
        }
        for (int i = 0; i < nfolds; i++) {
            trainData[i].close();
        }
    }
    
}
