/*
* Copyright (C) 2020 Information Retrieval Group at Universidad Autónoma
* de Madrid, http://ir.ii.uam.es.
*
* This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/
package es.uam.ir.ranksys.nn.user;

import es.uam.eps.ir.ranksys.fast.preference.FastPreferenceData;
import es.uam.eps.ir.ranksys.nn.user.neighborhood.UserNeighborhood;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Variant of the normalized user-based nearest neighbors recommender which
 * requires a minimum of neighbors rating an item in order to consider it
 * for recommendation.
 *
 * @author Rocío Cañamares
 * @author Pablo Castells
 *
 * @param <U> type of the users
 * @param <I> type of the items
 */
public class NormUserNeighborhoodRecommenderWithMinimum<U, I> extends NormUserNeighborhoodRecommender<U, I> {

    private final Function<Integer, Predicate<Integer>> minFilter;

    private Function<Integer, Predicate<Integer>> getMinFilter(int min) {
        return uidx -> {
            Int2DoubleOpenHashMap numMap = new Int2DoubleOpenHashMap();
            numMap.defaultReturnValue(0.0);
            neighborhood.getNeighbors(uidx).forEach(vs -> {
                data.getUidxPreferences(vs.v1).forEach(iv -> {
                    numMap.addTo(iv.v1, 1);
                });
            });
            return iidx -> numMap.get(iidx) >= min;
        };
    }

    /**
     * Constructor.
     *
     * @param data preference data
     * @param neighborhood item neighborhood
     * @param q exponent of the similarity
     * @param min minimum number of neighbors needed to be recommended
     */
    public NormUserNeighborhoodRecommenderWithMinimum(FastPreferenceData<U, I> data, UserNeighborhood<U> neighborhood, int q, int min) {
        super(data, neighborhood, q);
        this.minFilter = getMinFilter(min);
    }

    @Override
    public Int2DoubleMap getScoresMap(int uidx) {
        Predicate<Integer> filter = minFilter.apply(uidx);
        Int2DoubleOpenHashMap scoresMap = new Int2DoubleOpenHashMap();

        super.getScoresMap(uidx).int2DoubleEntrySet()
                .stream()
                .filter(e -> filter.test(e.getIntKey()))
                .forEach(e -> scoresMap.addTo(e.getIntKey(), e.getDoubleValue()));

        return scoresMap;
    }

}
