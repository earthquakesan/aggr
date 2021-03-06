package net.caspervg.aggr.worker.write;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import net.caspervg.aggr.aggregation.AbstractAggregation;
import net.caspervg.aggr.aggregation.average.AverageAggregation;
import net.caspervg.aggr.aggregation.basic.BasicAggregation;
import net.caspervg.aggr.aggregation.diff.DiffAggregation;
import net.caspervg.aggr.aggregation.grid.GridAggregation;
import net.caspervg.aggr.aggregation.kmeans.KMeansAggregation;
import net.caspervg.aggr.aggregation.time.TimeAggregation;
import net.caspervg.aggr.core.bean.Dataset;
import net.caspervg.aggr.core.bean.Measurement;
import net.caspervg.aggr.core.bean.UniquelyIdentifiable;
import net.caspervg.aggr.core.util.AggrContext;
import net.caspervg.aggr.worker.write.untyped.UntypedLiteral;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.DCTERMS;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Implementation of the {@link AggrWriter} interface that writes items to
 * a SPARQL endpoint through RDF4J.
 */
public class Rdf4jAggrWriter extends AbstractSparqlAggrWriter {

    private static final String DEFAULT_SERVICE = "http://localhost:8890/sparql/";
    private static final int QUERY_PARTITION = 1000;

    private boolean writeProvenance;
    private Repository repository;
    private ValueFactory valueFactory;
    private IRI geoPoint;
    private IRI ownMeas;
    private IRI ownDs;
    private IRI ownAggr;
    private IRI ownType;
    private IRI ownGridAggr;
    private IRI ownKMeansAggr;
    private IRI ownTimeAggr;
    private IRI ownBasicAggr;
    private IRI ownDiffAggr;
    private IRI ownAvgAggr;
    private IRI muUUID;

    public Rdf4jAggrWriter(Repository repository, boolean writeProvenance) {
        this.repository = repository;
        this.writeProvenance = writeProvenance;

        this.valueFactory = SimpleValueFactory.getInstance();
        UntypedLiteral.setDatatype(null);

        this.geoPoint = valueFactory.createIRI(GEO_PREFIX, "Point");
        this.ownMeas = valueFactory.createIRI(OWN_CLASS, "Measurement");
        this.ownDs = valueFactory.createIRI(OWN_CLASS, "Dataset");
        this.ownAggr = valueFactory.createIRI(OWN_CLASS, "Aggregation");
        this.ownType = valueFactory.createIRI(OWN_PROPERTY, "aggregation_type");
        this.ownGridAggr = valueFactory.createIRI(OWN_CLASS, "GridAggregation");
        this.ownKMeansAggr = valueFactory.createIRI(OWN_CLASS, "KMeansAggregation");
        this.ownTimeAggr = valueFactory.createIRI(OWN_CLASS, "TimeAggregation");
        this.ownBasicAggr = valueFactory.createIRI(OWN_CLASS, "BasicAggregation");
        this.ownDiffAggr = valueFactory.createIRI(OWN_CLASS, "DiffAggregation");
        this.ownAvgAggr = valueFactory.createIRI(OWN_CLASS, "AverageAggregation");
        this.muUUID = valueFactory.createIRI(MU_PREFIX, "uuid");
    }

    public Rdf4jAggrWriter(Repository repository) {
        this(repository, false);
    }

    public Rdf4jAggrWriter() {
        this(new SailRepository(new MemoryStore()));
    }

    @Override
    public void writeMeasurement(Measurement measurement, AggrContext context) {
        Resource measRes = measurementWithId(measurement.getUuid());

        add(measurementStatements(measurement, measRes));
    }

    @Override
    public void writeMeasurements(Iterable<Measurement> measurements, AggrContext context) {
        Set<Statement> statements = new HashSet<>();

        if (Iterables.isEmpty(measurements)) return;

        for (Measurement measurement : measurements) {
            Resource measRes = measurementWithId(measurement.getUuid());
            statements.addAll(measurementStatements(measurement, measRes));
        }

        add(statements);
    }

    @Override
    public void writeAggregation(TimeAggregation aggregation, AggrContext context) {
        Set<Statement> statements = new HashSet<>();
        IRI ownStart = valueFactory.createIRI(START_TIME_PROPERTY);
        IRI ownEnd = valueFactory.createIRI(END_TIME_PROPERTY);

        Resource aggRes = aggregationWithId(aggregation.getUuid());

        statements.addAll(aggregationStatements(aggregation, aggRes));

        // Start time of this time aggregation
        statements.add(
                valueFactory.createStatement(
                        aggRes,
                        ownStart,
                        literalTimestamp(aggregation.getStart())
                )
        );

        // End time of this time aggregation
        statements.add(
                valueFactory.createStatement(
                        aggRes,
                        ownEnd,
                        literalTimestamp(aggregation.getEnd())
                )
        );

        // Type of the aggregation
        statements.add(
                valueFactory.createStatement(
                        aggRes,
                        this.ownType,
                        this.ownTimeAggr
                )
        );

        add(statements);
    }

    @Override
    public void writeAggregation(KMeansAggregation aggregation, AggrContext context) {
        Set<Statement> statements = new HashSet<>();
        IRI ownIterations = valueFactory.createIRI(ITERATIONS_PROPERTY);
        IRI ownNumCentroids = valueFactory.createIRI(NUM_CENTROIDS_PROPERTY);

        Resource aggRes = aggregationWithId(aggregation.getUuid());

        statements.addAll(aggregationStatements(aggregation, aggRes));

        // Number of iterations of the KMeans aggregation
        statements.add(
                valueFactory.createStatement(
                        aggRes,
                        ownIterations,
                        valueFactory.createLiteral(BigInteger.valueOf(aggregation.getN()))
                )
        );

        // Number of means of the KMeans aggregation
        statements.add(
                valueFactory.createStatement(
                        aggRes,
                        ownNumCentroids,
                        valueFactory.createLiteral(BigInteger.valueOf(aggregation.getK()))
                )
        );

        // Type of the aggregation
        statements.add(
                valueFactory.createStatement(
                        aggRes,
                        this.ownType,
                        this.ownKMeansAggr
                )
        );

        add(statements);
    }

    @Override
    public void writeAggregation(GridAggregation aggregation, AggrContext context) {
        Set<Statement> statements = new HashSet<>();
        IRI ownGridSize = valueFactory.createIRI(GRID_SIZE_PROPERTY);

        Resource aggRes = aggregationWithId(aggregation.getUuid());

        statements.addAll(aggregationStatements(aggregation, aggRes));

        // Grid size of the grid aggregation
        statements.add(
                valueFactory.createStatement(
                        aggRes,
                        ownGridSize,
                        valueFactory.createLiteral(aggregation.getGridSize())
                )
        );

        // Type of the aggregation
        statements.add(
                valueFactory.createStatement(
                        aggRes,
                        this.ownType,
                        this.ownGridAggr
                )
        );

        add(statements);
    }

    @Override
    public void writeAggregation(BasicAggregation aggregation, AggrContext context) {
        Set<Statement> statements = new HashSet<>();

        Resource aggRes = aggregationWithId(aggregation.getUuid());
        statements.addAll(aggregationStatements(aggregation, aggRes));

        // Type of the aggregation
        statements.add(
                valueFactory.createStatement(
                        aggRes,
                        this.ownType,
                        this.ownBasicAggr
                )
        );

        add(statements);
    }

    @Override
    public void writeAggregation(DiffAggregation aggregation, AggrContext context) {
        Set<Statement> statements = new HashSet<>();

        Resource aggRes = aggregationWithId(aggregation.getUuid());
        statements.addAll(aggregationStatements(aggregation, aggRes));

        // Type of the aggregation
        statements.add(
                valueFactory.createStatement(
                        aggRes,
                        this.ownType,
                        this.ownDiffAggr
                )
        );

        // Subtrahend of the difference
        statements.add(
                valueFactory.createStatement(
                        aggRes,
                        valueFactory.createIRI(OWN_PROPERTY, "subtrahend"),
                        stringLiteral(aggregation.getOther())
                )
        );

        // Key of the value to retrieve
        statements.add(
                valueFactory.createStatement(
                        aggRes,
                        valueFactory.createIRI(OWN_PROPERTY, "key"),
                        stringLiteral(aggregation.getKey())
                )
        );

        add(statements);
    }

    @Override
    public void writeAggregation(AverageAggregation aggregation, AggrContext context) {
        Set<Statement> statements = new HashSet<>();

        Resource aggRes = aggregationWithId(aggregation.getUuid());
        statements.addAll(aggregationStatements(aggregation, aggRes));

        // Type of the aggregation
        statements.add(
                valueFactory.createStatement(
                        aggRes,
                        this.ownType,
                        this.ownAvgAggr
                )
        );

        statements.add(
                valueFactory.createStatement(
                        aggRes,
                        valueFactory.createIRI(OWN_PROPERTY, "others"),
                        stringLiteral(aggregation.getOthers())
                )
        );

        // Key of the value to retrieve
        statements.add(
                valueFactory.createStatement(
                        aggRes,
                        valueFactory.createIRI(OWN_PROPERTY, "key"),
                        stringLiteral(aggregation.getKey())
                )
        );

        // Expected amount of measurements per data point
        statements.add(
                valueFactory.createStatement(
                        aggRes,
                        valueFactory.createIRI(OWN_PROPERTY, "amount"),
                        valueFactory.createLiteral(BigInteger.valueOf(aggregation.getAmount()))
                )
        );

        add(statements);
    }

    protected Collection<Statement> measurementStatements(Measurement measurement, Resource measRes) {
        Set<Statement> statements = new HashSet<>();

        String id = measurement.getUuid();
        Set<UniquelyIdentifiable> parents = measurement.getParents();

        // Types of the measurement
        statements.add(
                valueFactory.createStatement(
                        measRes,
                        RDF.TYPE,
                        this.ownMeas
                )
        );

        // mu-UUID of the measurement
        statements.add(
                valueFactory.createStatement(
                        measRes,
                        this.muUUID,
                        stringLiteral(id)
                )
        );

        for (String key : measurement.getWriteKeys()) {
            Object measValue = measurement.getData().get(key);
            IRI type = null;
            Value value;

            if (measValue instanceof Integer) {
                value = valueFactory.createLiteral(BigInteger.valueOf((Integer) measValue));
            } else if (measValue instanceof Long) {
                value = valueFactory.createLiteral(BigInteger.valueOf((Long) measValue));
            } else if (measValue instanceof Double) {
                value = valueFactory.createLiteral((Double) measValue);
            } else if (measValue instanceof Instant) {
                type = DCTERMS.DATE;
                value =  literalTimestamp((Instant) measValue);
            } else {
                value = stringLiteral(measValue.toString());
            }

            if (type == null) {
                type =  valueFactory.createIRI(OWN_PROPERTY, key);
            }

            statements.add(
                    valueFactory.createStatement(
                            measRes,
                            type,
                            value
                    )
            );
        }

        if (writeProvenance) {
            for (UniquelyIdentifiable parent : parents) {
                statements.add(
                    valueFactory.createStatement(
                        measRes,
                        DCTERMS.SOURCE,
                        measurementWithId(parent.getUuid())
                    )
                );
            }
        }

        return statements;
    }

    protected Collection<Statement> aggregationStatements(AbstractAggregation aggregation, Resource aggRes) {
        Set<Statement> statements = new HashSet<>();

        // mu-UUID of the aggregation
        statements.add(
                valueFactory.createStatement(
                        aggRes,
                        muUUID,
                        stringLiteral(aggregation.getUuid())
                )
        );

        // Dataset of the aggregation
        statements.add(
                valueFactory.createStatement(
                        aggRes,
                        DCTERMS.IS_PART_OF,
                        datasetWithId(aggregation.getDataset().getUuid())
                )
        );

        // Supertype of the aggregation
        statements.add(
                valueFactory.createStatement(
                        aggRes,
                        RDF.TYPE,
                        this.ownAggr
                )
        );

        // Data path of the aggregation
        statements.add(
                valueFactory.createStatement(
                        aggRes,
                        DCTERMS.REFERENCES,
                        valueFactory.createIRI(
                                aggregation.getDataPath()
                        )
                )
        );

        // Creation time of the aggregation
        statements.add(
                valueFactory.createStatement(
                        aggRes,
                        DCTERMS.DATE,
                        literalTimestamp(aggregation.getCreated())
                )
        );

        if (writeProvenance) {
            // Sources of the aggregation
            for (Measurement measurement : aggregation.getSources()) {
                statements.add(
                        valueFactory.createStatement(
                                measurementWithId(measurement.getUuid()),
                                DCTERMS.IS_REPLACED_BY,
                                aggRes
                        )
                );
            }

            for (UniquelyIdentifiable uniquelyIdentifiable : aggregation.getComponents()) {
                statements.add(
                        valueFactory.createStatement(
                                measurementWithId(uniquelyIdentifiable.getUuid()),
                                DCTERMS.IS_PART_OF,
                                aggRes
                        )
                );
            }
        }

        return statements;
    }

    @Override
    public void writeDataset(Dataset dataset, AggrContext context) {
        Set<Statement> statements = new HashSet<>();

        Resource dsRes = datasetWithId(dataset.getUuid());

        // mu-UUID of the dataset
        statements.add(
                valueFactory.createStatement(
                        dsRes,
                        muUUID,
                        stringLiteral(dataset.getUuid())
                )
        );

        // Title of the dataset
        statements.add(
                valueFactory.createStatement(
                        dsRes,
                        DCTERMS.TITLE,
                        stringLiteral(dataset.getTitle())
                )
        );

        // Type of the dataset
        statements.add(
                valueFactory.createStatement(
                        dsRes,
                        RDF.TYPE,
                        this.ownDs
                )
        );

        add(statements);
    }

    /**
     * Lazily creates or retrieves a Dataset resource with given id
     *
     * @param id id of the dataset
     * @return RDF4J resource for the dataset
     */
    private Resource datasetWithId(String id) {
        return valueFactory.createIRI(DATASET_URI_PREFIX, id);
    }

    /**
     * Lazily creates or retrieves an Aggregation resource with given id
     *
     * @param id id of the aggregation
     * @return RDF4J resource for the aggregation
     */
    private Resource aggregationWithId(String id) {
        return valueFactory.createIRI(AGGREGATION_URI_PREFIX, id);
    }

    /**
     * Lazily creates or retrieves a Measurement resource with given id
     *
     * @param id id of the measurement
     * @return RDF4J resource for the measurement
     */
    private Resource measurementWithId(String id) {
        return valueFactory.createIRI(MEASUREMENT_URI_PREFIX, id);
    }

    /**
     * Lazily creates or retrieves a Centroid resource with given id
     *
     * @param id id of the centroid
     * @return RDF4J resource for the centroid
     */
    private Resource centroidWithId(String id) {
        return valueFactory.createIRI(CENTROID_URI_PREFIX, id);
    }

    /**
     * Creates a {@link UntypedLiteral} that wraps the content, as a workaround for RDF4J's strong typing
     * of all strings as <code>xsd:string</code>.
     *
     * @param content Content to wrap
     * @return Wrapped untyped literal
     */
    private Value stringLiteral(String content) {
        return new UntypedLiteral(content);
    }

    /**
     * Converts a {@link LocalDateTime} instance to a {@link Literal}.
     *
     * @param dateTime Date time to convert
     * @return Converted literal
     */
    private Literal literalTimestamp(Instant dateTime) {
        return valueFactory.createLiteral(
                Date.from(
                        dateTime
                )
        );
    }

    private Literal literalTimestamp(LocalDateTime dateTime) {
        return literalTimestamp(dateTime.toInstant(ZoneOffset.UTC));
    }

    /**
     * Adds a collection of {@link Statement} to the SPARQL repository.
     *
     * @param statements Statements to add
     */
    private void add(Collection<Statement> statements) {
        List<Statement> statementList = new ArrayList<>(statements);
        List<List<Statement>> statementPartitions = Lists.partition(statementList, QUERY_PARTITION);

        try (RepositoryConnection conn = getConnection()) {
            IRI graphIri = valueFactory.createIRI(DEFAULT_GRAPH);
            for (List<Statement> partition : statementPartitions) {
                conn.add(partition, graphIri);
            }
        }
    }

    private RepositoryConnection getConnection() {
        if (! this.repository.isInitialized()) {
            this.repository.initialize();
        }
        return repository.getConnection();
    }
}
