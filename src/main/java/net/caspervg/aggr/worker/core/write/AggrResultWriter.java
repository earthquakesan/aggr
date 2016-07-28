package net.caspervg.aggr.worker.core.write;

import net.caspervg.aggr.worker.core.bean.Dataset;
import net.caspervg.aggr.worker.core.bean.Measurement;
import net.caspervg.aggr.worker.core.bean.aggregation.AggregationResult;
import net.caspervg.aggr.worker.core.bean.aggregation.GridAggregation;
import net.caspervg.aggr.worker.core.bean.aggregation.KMeansAggregation;
import net.caspervg.aggr.worker.core.bean.aggregation.TimeAggregation;
import net.caspervg.aggr.worker.core.util.AggrContext;

public interface AggrResultWriter {
    void writeGridAggregation(AggregationResult<GridAggregation, Measurement> result, AggrContext context);
    void writeKMeansAggregation(AggregationResult<KMeansAggregation, Measurement> result, AggrContext context);
    void writeTimeAggregation(AggregationResult<TimeAggregation, Measurement> result, AggrContext context);
    void writeDataset(Dataset dataset, AggrContext context);
}