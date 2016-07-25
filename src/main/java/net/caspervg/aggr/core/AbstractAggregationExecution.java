package net.caspervg.aggr.core;

import net.caspervg.aggr.core.bean.aggregation.AbstractAggregation;
import net.caspervg.aggr.core.bean.aggregation.AggregationResult;
import net.caspervg.aggr.core.read.AggrReader;
import net.caspervg.aggr.core.read.CsvAggrReader;
import net.caspervg.aggr.core.read.JenaAggrReader;
import net.caspervg.aggr.core.util.AggrCommand;
import net.caspervg.aggr.core.util.AggrContext;
import net.caspervg.aggr.core.util.untyped.UntypedSPARQLRepository;
import net.caspervg.aggr.core.write.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.Path;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;

import java.io.*;

public abstract class AbstractAggregationExecution implements AggregationExecution {

    /**
     * Builds a Spark context for Java execution
     * @param ac Demands of the user
     * @return Spark Context with some
     */
    protected JavaSparkContext getSparkContext(AggrCommand ac) {
        SparkConf conf = new SparkConf()
                .setAppName("KMeansAggr")
                .setMaster(ac.getSparkMasterUrl())
                .set("spark.eventLog.enabled", "true")
                .set("eventLog.enabled", "true");
        return new JavaSparkContext(conf);
    }

    /**
     * Retrieve a suitable {@link AggrReader} based on the user's demands
     *
     * @param ac Demands of the user
     * @param ctx Context of the execution
     * @return Suitable instance of {@link AggrReader} with a pre-set {@link BufferedReader}
     * @throws IOException if the input cannot be opened or read
     */
    protected AggrReader getReader(AggrCommand ac, AggrContext ctx) throws IOException {
        if (ac.getInput().toLowerCase().contains("sparql")) {
            return new JenaAggrReader();
        } else {
            if (!ac.isHdfs()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(ac.getInput())));
                return new CsvAggrReader(reader);
            } else {
                Path path = new Path(ac.getInput());
                BufferedReader reader = new BufferedReader(new InputStreamReader(ctx.getFileSystem().open(path)));
                return new CsvAggrReader(reader);
            }
        }
    }

    /**
     * Retrieve a suitable AggrResultWriter based on the user's demands
     *
     * @param aggrResult Type of the aggregation result
     * @param ac Demands of the user
     * @param ctx Execution context
     * @param <A> Type of the aggregation
     * @param <M> Type of the aggregation result
     * @return Suitable writer, possibly a composite {@link AggrWriter} that selects different channels for different
     * types of output
     */
    protected <A extends AbstractAggregation, M> AggrResultWriter getWriter(
            AggregationResult<A, M> aggrResult,
            AggrCommand ac,
            AggrContext ctx) {
        AggrWriter metaWriter = new Rdf4jAggrWriter(new UntypedSPARQLRepository(ac.getService()), ac.isWriteProvenance());
        AggrWriter dataWriter;

        if (ac.isWriteDataCsv()) {
            try {
                String hdfsUrl = ac.getHdfsUrl();
                String dirPath = ac.getOutput();
                String fileName = aggrResult.getAggregation().getUuid() + ".csv";

                if (ac.isSpark()) {
                    if (StringUtils.isNotBlank(hdfsUrl)) {
                        Path parent = new Path(dirPath);
                        Path child = new Path(parent, fileName);
                        FSDataOutputStream os = ctx.getFileSystem().create(child, false);
                        dataWriter = new CsvAggrWriter(new PrintWriter(os));
                    } else {
                        dataWriter = new CsvAggrWriter(new PrintWriter(new File(dirPath + "/" + fileName)));
                    }
                } else {
                    dataWriter = new CsvAggrWriter(new PrintWriter(new File(dirPath + "/" + fileName)));
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                throw new RuntimeException(ex);
            }

            return new CompositeAggrWriter(dataWriter, metaWriter, ac.isWriteProvenance());     // Split data to CSV, metadata to triple store
        }

        return new CompositeAggrWriter(metaWriter, metaWriter, ac.isWriteProvenance());         // Write data and metadata to the triple store
    }

    /**
     * Stop the Spark Context if it exists
     * @param context Context of the execution
     */
    protected void stop(AggrContext context) {
        if (context.getSparkContext() != null) {
            context.getSparkContext().stop();
        }
    }

}