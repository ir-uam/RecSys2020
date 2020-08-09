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
import es.uam.eps.ir.ranksys.nn.user.UserNeighborhoodRecommender;
import es.uam.eps.ir.ranksys.nn.user.neighborhood.UserNeighborhood;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import static java.lang.Math.pow;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Normalized user-based nearest neighbors recommender.
 *
 * @author Rocío Cañamares
 * @author Pablo Castells
 *
 * @param <U> type of the users
 * @param <I> type of the items
 */
public class NormUserNeighborhoodRecommender<U, I> extends UserNeighborhoodRecommender<U, I> {

    private final Function<U, BiFunction<I, Double, Double>> normalizationMap;

    /**
     * Constructor.
     *
     * @param data preference data
     * @param neighborhood user neighborhood
     * @param q exponent of the similarity
     */
    public NormUserNeighborhoodRecommender(FastPreferenceData<U, I> data, UserNeighborhood<U> neighborhood, int q) {
        super(data, neighborhood, q);
        this.normalizationMap = user -> {
            int uidx = data.user2uidx(user);
            Int2DoubleOpenHashMap normMap = new Int2DoubleOpenHashMap();

            normMap.defaultReturnValue(0.0);
            neighborhood.getNeighbors(uidx).forEach(vs -> {
                double w = pow(vs.v2, q);
                data.getUidxPreferences(vs.v1).forEach(iv -> {
                    normMap.addTo(iv.v1, w);
                });
            });
            return (item, value) -> value * 1.0 / normMap.get(data.item2iidx(item));
        };
    }

    @Override
    public Int2DoubleMap getScoresMap(int uidx) {
        Int2DoubleMap scoresMap = super.getScoresMap(uidx);
        BiFunction<I, Double, Double> norm = normalizationMap.apply(data.uidx2user(uidx));
        scoresMap.replaceAll((idx, value) -> norm.apply(data.iidx2item(idx), value));

        return scoresMap;
    }
}
