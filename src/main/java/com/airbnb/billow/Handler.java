package com.airbnb.billow;

import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.identitymanagement.model.AccessKeyMetadata;
import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.DBInstanceStatusInfo;
import com.amazonaws.services.rds.model.PendingModifiedValues;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.net.HttpHeaders;
import lombok.extern.slf4j.Slf4j;
import ognl.Ognl;
import ognl.OgnlException;
import org.apache.http.entity.ContentType;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

@Slf4j
public class Handler extends AbstractHandler {
    public static final SimpleFilterProvider NOOP_INSTANCE_FILTER = new SimpleFilterProvider().addFilter(EC2Instance.INSTANCE_FILTER,
            SimpleBeanPropertyFilter.serializeAllExcept());
    public static final SimpleFilterProvider NOOP_TABLE_FILTER = new SimpleFilterProvider().addFilter(DynamoTable.Table_FILTER,
        SimpleBeanPropertyFilter.serializeAllExcept());
    private final ObjectMapper mapper;
    private final MetricRegistry registry;
    private final AWSDatabaseHolder dbHolder;
    private final long maxDBAgeInMs;

    public static abstract class DBInstanceMixin extends DBInstance {
        @JsonIgnore
        @Override
        public abstract Boolean isMultiAZ();

        @JsonIgnore
        @Override
        public abstract Boolean isAutoMinorVersionUpgrade();

        @JsonIgnore
        @Override
        public abstract Boolean isPubliclyAccessible();
    }

    public static abstract class PendingModifiedValuesMixin extends PendingModifiedValues {
        @JsonIgnore
        @Override
        public abstract Boolean isMultiAZ();
    }

    public static abstract class DBInstanceStatusInfoMixin extends DBInstanceStatusInfo {
        @JsonIgnore
        @Override
        public abstract Boolean isNormal();
    }

    public Handler(MetricRegistry registry, AWSDatabaseHolder dbHolder, long maxDBAgeInMs) {
        this.mapper = new ObjectMapper();
        this.mapper.addMixInAnnotations(DBInstance.class, DBInstanceMixin.class);
        this.mapper.addMixInAnnotations(PendingModifiedValues.class, PendingModifiedValuesMixin.class);
        this.mapper.addMixInAnnotations(DBInstanceStatusInfo.class, DBInstanceStatusInfoMixin.class);
        this.mapper.registerModule(new JodaModule());
        this.mapper.registerModule(new GuavaModule());
        this.registry = registry;
        this.dbHolder = dbHolder;
        this.maxDBAgeInMs = maxDBAgeInMs;
    }

    @Override
    public void handle(String target,
                       Request baseRequest,
                       HttpServletRequest request,
                       HttpServletResponse response) {
        try {
            final Map<String, String[]> paramMap = request.getParameterMap();

            final AWSDatabase current = dbHolder.getCurrent();

            final long age = current.getAgeInMs();
            final float ageInSeconds = (float) age / 1000.0f;
            response.setHeader("Age", String.format("%.3f", ageInSeconds));
            response.setHeader("Cache-Control", String.format("public, max-age=%d", dbHolder.getCacheTimeInMs() / 1000));

            switch (target) {
                case "/ec2":
                    handleComplexEC2(response, paramMap, current);
                    break;
                case "/ec2/all":
                    handleSimpleRequest(response, current.getEc2Instances());
                    break;
                case "/rds/all":
                    // handleSimpleRequest(response, current.getRdsInstances());
                    break;
                case "/ec2/sg":
                    handleSimpleRequest(response, current.getEc2SGs());
                    break;
                case "/iam": // backwards compatibility with documented feature
                    final ArrayList<AccessKeyMetadata> justKeys = Lists.<AccessKeyMetadata>newArrayList();
                    for (IAMUserWithKeys userWithKeys : current.getIamUsers())
                        justKeys.addAll(userWithKeys.getKeys());
                    handleSimpleRequest(response, justKeys);
                    break;
                case "/iam/users":
                    handleSimpleRequest(response, current.getIamUsers());
                    break;
                case "/dynamo":
                    handleComlexDynamo(response, paramMap, current);
                    break;
                default:
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    break;
            }
        } finally {
            baseRequest.setHandled(true);
        }
    }

    private void handleSimpleRequest(HttpServletResponse response, Object o) {
        try {
            response.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
            mapper.writer(NOOP_INSTANCE_FILTER).writeValue(response.getOutputStream(), o);
        } catch (Throwable e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            log.error("Error handling request", e);
        }
    }

    private void handleComlexDynamo(HttpServletResponse response,
                                    Map<String, String[]> params,
                                    AWSDatabase db) {
        final String query = getQuery(params);
        final String sort = getSort(params);
        final int limit = getLimit(params);
        final Set<String> fields = getFields(params);

        response.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
        try {
            try {
                final Collection<DynamoTable> queriedTables = listTablesFromQueryExpression(query, db);
                final Collection<DynamoTable> sortedTables = sortTablesWithExpression(queriedTables, sort);
                final Iterable<DynamoTable> servedTables = Iterables.limit(sortedTables, limit);

                if (!(servedTables.iterator().hasNext())) {
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                } else {
                    final ServletOutputStream outputStream = response.getOutputStream();
                    final SimpleFilterProvider filterProvider;
                    if (fields != null) {
                        log.debug("filtered output ({})", fields);
                        filterProvider = new SimpleFilterProvider()
                            .addFilter(DynamoTable.Table_FILTER, SimpleBeanPropertyFilter.filterOutAllExcept(fields));
                    } else {
                        log.debug("unfiltered output");
                        filterProvider = NOOP_TABLE_FILTER;
                    }
                    mapper.writer(filterProvider).writeValue(outputStream, Lists.newArrayList(servedTables));
                }
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                final ServletOutputStream outputStream = response.getOutputStream();
                outputStream.print(e.toString());
                outputStream.close();
            }
        } catch (IOException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            log.error("I/O error handling EC2 request", e);
        }
    }



    private void handleComplexEC2(HttpServletResponse response,
                                  Map<String, String[]> params,
                                  AWSDatabase db) {
        final String query = getQuery(params);
        final String sort = getSort(params);
        final int limit = getLimit(params);
        final Set<String> fields = getFields(params);

        response.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());

        try {
            try {
                final Collection<EC2Instance> queriedInstances = listInstancesFromQueryExpression(query, db);
                final Collection<EC2Instance> sortedInstances = sortInstancesWithExpression(queriedInstances, sort);
                final Iterable<EC2Instance> servedInstances = Iterables.limit(sortedInstances, limit);

                if (!(servedInstances.iterator().hasNext())) {
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                } else {
                    final ServletOutputStream outputStream = response.getOutputStream();
                    final SimpleFilterProvider filterProvider;
                    if (fields != null) {
                        log.debug("filtered output ({})", fields);
                        filterProvider = new SimpleFilterProvider()
                                .addFilter(EC2Instance.INSTANCE_FILTER, SimpleBeanPropertyFilter.filterOutAllExcept(fields));
                    } else {
                        log.debug("unfiltered output");
                        filterProvider = NOOP_INSTANCE_FILTER;
                    }
                    mapper.writer(filterProvider).writeValue(outputStream, Lists.newArrayList(servedInstances));
                }
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                final ServletOutputStream outputStream = response.getOutputStream();
                outputStream.print(e.toString());
                outputStream.close();
            }
        } catch (IOException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            log.error("I/O error handling EC2 request", e);
        }
    }

    Collection<DynamoTable> listTablesFromQueryExpression(final String expression, final AWSDatabase db)
        throws OgnlException {
        final Collection<DynamoTable> allTables = db.getDynamoTables().values();
        if (expression == null)
            return allTables;

        final Object compiled = Ognl.parseExpression(expression);
        final List<DynamoTable> tables = new ArrayList<>();

        for (DynamoTable table : allTables) {
            final Object value = Ognl.getValue(compiled, tables);
            if (value instanceof Boolean && (Boolean) value)
                tables.add(table);
        }

        return tables;
    }

    Collection<DynamoTable> sortTablesWithExpression(final Collection<DynamoTable> tables,
                                                        final String expression)
        throws OgnlException {
        if (expression == null)
            return tables;

        final Object compiled = Ognl.parseExpression(expression);

        final ArrayList<DynamoTable> result = new ArrayList<>(tables);
        Collections.sort(result, new Comparator<DynamoTable>() {
            public int compare(DynamoTable o1, DynamoTable o2) {
                try {
                    final Object v1 = Ognl.getValue(compiled, o1);
                    final Object v2 = Ognl.getValue(compiled, o2);

                    if (v1 instanceof Comparable) {
                        return ((Comparable) v1).compareTo(v2);
                    }
                } catch (OgnlException e) {
                    return 0;
                }
                return 0;
            }
        });

        return result;
    }

    Collection<EC2Instance> listInstancesFromQueryExpression(final String expression, final AWSDatabase db)
            throws OgnlException {
        final Collection<EC2Instance> allInstances = db.getEc2Instances().values();
        if (expression == null)
            return allInstances;

        final Object compiled = Ognl.parseExpression(expression);
        final List<EC2Instance> instances = new ArrayList<>();

        for (EC2Instance instance : allInstances) {
            final Object value = Ognl.getValue(compiled, instance);
            if (value instanceof Boolean && (Boolean) value)
                instances.add(instance);
        }

        return instances;
    }

    Collection<EC2Instance> sortInstancesWithExpression(final Collection<EC2Instance> instances,
                                                        final String expression)
            throws OgnlException {
        if (expression == null)
            return instances;

        final Object compiled = Ognl.parseExpression(expression);

        final ArrayList<EC2Instance> result = new ArrayList<>(instances);
        Collections.sort(result, new Comparator<EC2Instance>() {
            public int compare(EC2Instance o1, EC2Instance o2) {
                try {
                    final Object v1 = Ognl.getValue(compiled, o1);
                    final Object v2 = Ognl.getValue(compiled, o2);

                    if (v1 instanceof Comparable) {
                        return ((Comparable) v1).compareTo(v2);
                    }
                } catch (OgnlException e) {
                    return 0;
                }
                return 0;
            }
        });

        return result;
    }

    private String getQuery(Map<String, String[]> params) {
        final String[] qs = params.get("q");
        if (qs != null)
            return qs[0];
        final String[] queries = params.get("query");
        if (queries != null)
            return queries[0];
        return null;
    }

    private String getSort(Map<String, String[]> params) {
        final String[] ss = params.get("s");
        if (ss != null)
            return ss[0];
        final String[] sorts = params.get("sort");
        if (sorts != null)
            return sorts[0];
        return null;
    }

    private int getLimit(Map<String, String[]> params) {
        final String[] ls = params.get("l");
        if (ls != null)
            return Integer.parseInt(ls[0]);
        final String[] limits = params.get("limit");
        if (limits != null)
            return Integer.parseInt(limits[0]);
        return Integer.MAX_VALUE;
    }

    private Set<String> getFields(Map<String, String[]> params) {
        final String[] fs = params.get("f");
        if (fs != null)
            return Sets.newHashSet(Splitter.on(',').split(fs[0]));
        final String[] fields = params.get("fields");
        if (fields != null)
            return Sets.newHashSet(Splitter.on(',').split(fields[0]));
        return null;
    }
}
