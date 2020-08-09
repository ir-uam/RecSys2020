/*
* Copyright (C) 2020 Information Retrieval Group at Universidad Autónoma
* de Madrid, http://ir.ii.uam.es.
*
* This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/
package es.uam.ir.ranksys.rec.fast.basic;

import es.uam.eps.ir.ranksys.fast.FastRecommendation;
import es.uam.eps.ir.ranksys.fast.preference.FastPreferenceData;
import es.uam.eps.ir.ranksys.rec.fast.AbstractFastRecommender;
import static java.util.Comparator.comparingDouble;
import java.util.List;
import java.util.function.IntPredicate;
import static java.util.stream.Collectors.toList;
import org.ranksys.core.util.tuples.Tuple2id;
import static org.ranksys.core.util.tuples.Tuples.tuple;
import static java.lang.Math.min;

/**
 * Average rating recommender. Non-personalized recommender that returns the
 * items with the greatest average rating value, according to the preference
 * data provided.
 *
 * @author Rocío Cañamares
 * @author Pablo Castells
 *
 * @param <U> type of the users
 * @param <I> type of the items
 */
public class AverageRatingRecommender<U, I> extends AbstractFastRecommender<U, I> {

    private final List<Tuple2id> popList;

    /**
     * Constructor.
     *
     * @param data preference data
     * @param threshold ratings with a value larger than or equal to this
     * threshold are considered relevant
     */
    public AverageRatingRecommender(FastPreferenceData<U, I> data, double threshold) {
        super(data, data);

        double p = data.getAllUsers().mapToDouble(user -> data.getUserPreferences(user).filter(up -> up.v2 >= threshold).count()).sum() / data.numPreferences();
        double mu = 1;
        popList = data.getIidxWithPreferences()
                .mapToObj(iidx -> tuple(iidx, (data.getIidxPreferences(iidx).filter(ip -> ip.v2 >= threshold).count() + mu * p) * 1.0 / (data.numUsers(iidx) + mu)))
                .sorted(comparingDouble(Tuple2id::v2).reversed())
                .collect(toList());
    }

    @Override
    public FastRecommendation getRecommendation(int uidx, int maxLength, IntPredicate filter) {

        List<Tuple2id> items = popList.stream()
                .filter(is -> filter.test(is.v1))
                .limit(min(maxLength, popList.size()))
                .collect(toList());

        return new FastRecommendation(uidx, items);
    }
}
