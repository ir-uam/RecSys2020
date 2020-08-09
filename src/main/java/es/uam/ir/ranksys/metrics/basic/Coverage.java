/*
* Copyright (C) 2020 Information Retrieval Group at Universidad Autónoma
* de Madrid, http://ir.ii.uam.es.
*
* This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/
package es.uam.ir.ranksys.metrics.basic;

import es.uam.eps.ir.ranksys.core.Recommendation;
import es.uam.eps.ir.ranksys.metrics.AbstractRecommendationMetric;

/**
 *
 * @author Rocío Cañamares
 * @author Pablo Castells
 */
public class Coverage<U,I> extends AbstractRecommendationMetric<U, I> {
    
    private final int k;

    /**
     * 
     * @param k 
     */
    public Coverage(int k) {
        this.k = k;
    }
    
    /**
     * 
     */
    public Coverage() {
        this.k = 1;
    }

    /**
     * Returns a score for the recommendation list.
     *
     * @param recommendation recommendation list
     * @return score of the metric to the recommendation
     */
    @Override
    public double evaluate(Recommendation<U, I> recommendation) {
        return Math.min(recommendation.getItems().size(),k) * 1.0 / k;
    }
}