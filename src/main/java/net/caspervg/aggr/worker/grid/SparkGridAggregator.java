package net.caspervg.aggr.worker.grid;

import com.google.common.collect.Lists;
import net.caspervg.aggr.worker.core.bean.*;
import net.caspervg.aggr.worker.core.bean.aggregation.AggregationResult;
import net.caspervg.aggr.worker.core.bean.aggregation.GridAggregation;
import net.caspervg.aggr.worker.core.util.AggrContext;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.function.Function;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class SparkGridAggregator extends AbstractGridAggregator implements Serializable {

    @Override
    public Iterable<AggregationResult<GridAggregation, Measurement>> aggregate(Dataset dataset,
                                                                                       Iterable<Measurement> measurements,
                                                                                       AggrContext context) {
        Objects.requireNonNull(context.getSparkContext());

        double gridSize = Double.parseDouble(
                context.getParameters().getOrDefault(GRID_SIZE_PARAM, DEFAULT_GRID_SIZE)
        );

        JavaRDD<Measurement> measRDD = context.getSparkContext().parallelize(Lists.newArrayList(measurements));

        // Map each measurement so that it sits on top of the grid (rounding)
        JavaRDD<Measurement> roundedMeasRDD = measRDD.map((Function<Measurement, Measurement>) parent -> {
            Double[] parentVec = parent.getVector();
            Double[] roundedVec = new Double[parentVec.length];

            for (int i = 0; i < parentVec.length; i++) {
                roundedVec[i] = (double) Math.round(parentVec[i] / gridSize) * gridSize;
            }

            Measurement child = context.newMeasurement();
            Set<UniquelyIdentifiable> parents = new HashSet<>();
            parents.add(parent);

            child.setParents(parents);
            child.setData(parent.getData());
            child.setVector(roundedVec);

            return child;
        });

        List<Measurement> childMeasurements = roundedMeasRDD.collect();

        // Return the result of the aggregation
        return Lists.newArrayList(
                new AggregationResult<>(
                        new GridAggregation(dataset,
                                gridSize,
                                Lists.newArrayList(childMeasurements)
                        ),
                        childMeasurements
                )
        );
    }
}