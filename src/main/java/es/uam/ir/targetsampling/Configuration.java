/*
* Copyright (C) 2020 Information Retrieval Group at Universidad Autónoma
* de Madrid, http://ir.ii.uam.es.
*
* This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/
package es.uam.ir.targetsampling;

import es.uam.ir.filler.Filler.Mode;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;

/**
 *
 * @author Rocío Cañamares
 * @author Pablo Castells
 */
public class Configuration {

    private final String dataPath;
    private final String testPath;
    private final int threshold;

    private String resultsPath;

    private final int nFolds;
    private final int[] targetSizes;
    private final int cutoff;
    private Mode fillMode;
    
    //Params when all recs
    private boolean allRecs;
    private int[] knnParamK = null; 
    private int[] normKnnParamK = null; 
    private int[] normKnnParamMin = null; 
    private int[] imfParamK = null;
    private double[] imfParamLambda = null;
    private double[] imfParamAlpha = null;

    //kNN params
    private int knnFullParamK = -1;
    private int knnTestParamK = -1;
    private int normKnnFullParamK = -1;
    private int normKnnTestParamK = -1;
    private int normKnnFullParamMin = -1;
    private int normKnnTestParamMin = -1;

    //iMF params
    private int imfFullParamK = -1;
    private int imfTestParamK = -1;
    private double imfFullParamLambda = -1;
    private double imfTestParamLambda = -1;
    private double imfFullParamAlpha = -1;
    private double imfTestParamAlpha = -1;

    /**
     * 
     * @param propFileName
     * @throws IOException 
     */
    public Configuration(String propFileName) throws IOException {

        Properties prop = new Properties();

        try ( InputStream inputStream = new FileInputStream(propFileName)) {
            if (inputStream != null) {
                prop.load(inputStream);
            } else {
                throw new FileNotFoundException("property file '" + propFileName + "' not found in the classpath");
            }

            this.dataPath = prop.getProperty("data.path");
            this.testPath = prop.getProperty("data.test.path");
            this.threshold = Integer.valueOf(prop.getProperty("data.threshold"));
            this.resultsPath = prop.getProperty("results.path");
            this.nFolds = Integer.valueOf(prop.getProperty("crossvalidation.nfolds"));
            this.cutoff = Integer.valueOf(prop.getProperty("evaluation.cutoff"));
            switch (prop.getProperty("fill.mode")){
                case "rnd":
                    this.fillMode = Mode.RND;
                    break;
                default:
                    this.fillMode = Mode.NONE;
                    break;
            }
 
            String[] targetSizeTokens = prop.getProperty("targetselection.targetsizes").split(",");
            this.targetSizes = new int[targetSizeTokens.length];
            for (int i = 0; i < targetSizeTokens.length; i++) {
                this.targetSizes[i] = Integer.valueOf(targetSizeTokens[i]);
            }
            
            this.allRecs=Boolean.valueOf(prop.getProperty("algorithms.run.all"));
            if (this.allRecs) {
                this.knnParamK = Arrays.stream(prop.getProperty("algorithms.knn.k").split(",")).mapToInt(str -> Integer.valueOf(str)).toArray();
                
                this.normKnnParamK = Arrays.stream(prop.getProperty("algorithms.normknn.k").split(",")).mapToInt(str -> Integer.valueOf(str)).toArray();
                this.normKnnParamMin = Arrays.stream(prop.getProperty("algorithms.normknn.min").split(",")).mapToInt(str -> Integer.valueOf(str)).toArray();
                
                this.imfParamK = Arrays.stream(prop.getProperty("algorithms.imf.k").split(",")).mapToInt(str -> Integer.valueOf(str)).toArray();
                this.imfParamLambda = Arrays.stream(prop.getProperty("algorithms.imf.lambda").split(",")).mapToDouble(str -> Double.valueOf(str)).toArray();
                this.imfParamAlpha = Arrays.stream(prop.getProperty("algorithms.imf.alpha").split(",")).mapToDouble(str -> Double.valueOf(str)).toArray();
            } else {
                this.knnFullParamK = Integer.valueOf(prop.getProperty("algorithms.full.knn.k"));
                this.knnTestParamK = Integer.valueOf(prop.getProperty("algorithms.test.knn.k"));
                
                this.normKnnFullParamK = Integer.valueOf(prop.getProperty("algorithms.full.normknn.k"));
                this.normKnnTestParamK = Integer.valueOf(prop.getProperty("algorithms.test.normknn.k"));
                this.normKnnFullParamMin = Integer.valueOf(prop.getProperty("algorithms.full.normknn.min"));
                this.normKnnTestParamMin = Integer.valueOf(prop.getProperty("algorithms.test.normknn.min"));
                
                this.imfFullParamK = Integer.valueOf(prop.getProperty("algorithms.full.imf.k"));
                this.imfTestParamK = Integer.valueOf(prop.getProperty("algorithms.test.imf.k"));
                this.imfFullParamLambda = Double.valueOf(prop.getProperty("algorithms.full.imf.lambda"));
                this.imfTestParamLambda = Double.valueOf(prop.getProperty("algorithms.test.imf.lambda"));
                this.imfFullParamAlpha = Double.valueOf(prop.getProperty("algorithms.full.imf.alpha"));
                this.imfTestParamAlpha = Double.valueOf(prop.getProperty("algorithms.test.imf.alpha"));
            }
        }
    }

    public String getDataPath() {
        return dataPath;
    }

    public String getTestPath() {
        return testPath;
    }

    public int getThreshold() {
        return threshold;
    }

    public String getResultsPath() {
        return resultsPath;
    }

    public int getNFolds() {
        return nFolds;
    }

    public int[] getTargetSizes() {
        return targetSizes;
    }

    public int getCutoff() {
        return cutoff;
    }

    public Mode getFillMode() {
        return fillMode;
    }

    public boolean isAllRecs() {
        return allRecs;
    }

    public int[] getKnnParamK() {
        return knnParamK;
    }

    public int[] getNormKnnParamK() {
        return normKnnParamK;
    }

    public int[] getNormKnnParamMin() {
        return normKnnParamMin;
    }

    public int[] getImfParamK() {
        return imfParamK;
    }

    public double[] getImfParamLambda() {
        return imfParamLambda;
    }

    public double[] getImfParamAlpha() {
        return imfParamAlpha;
    }

    public int getKnnFullParamK() {
        return knnFullParamK;
    }

    public int getKnnTestParamK() {
        return knnTestParamK;
    }

    public int getNormKnnFullParamK() {
        return normKnnFullParamK;
    }

    public int getNormKnnTestParamK() {
        return normKnnTestParamK;
    }

    public int getNormKnnFullParamMin() {
        return normKnnFullParamMin;
    }

    public int getNormKnnTestParamMin() {
        return normKnnTestParamMin;
    }

    public int getImfFullParamK() {
        return imfFullParamK;
    }

    public int getImfTestParamK() {
        return imfTestParamK;
    }

    public double getImfFullParamLambda() {
        return imfFullParamLambda;
    }

    public double getImfTestParamLambda() {
        return imfTestParamLambda;
    }

    public double getImfFullParamAlpha() {
        return imfFullParamAlpha;
    }

    public double getImfTestParamAlpha() {
        return imfTestParamAlpha;
    }

    public void setFillMode(Mode fillMode) {
        this.fillMode = fillMode;
    }

    public void setAllRecs(boolean allRecs) {
        this.allRecs = allRecs;
    }

    public void setResultsPath(String resultsPath) {
        this.resultsPath = resultsPath;
    }
    
    
    
}
