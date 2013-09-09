package com.airbnb.billow;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import com.google.common.net.HttpHeaders;
import lombok.extern.slf4j.Slf4j;
import ognl.Ognl;
import ognl.OgnlException;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

@Slf4j
public class Handler extends AbstractHandler {
    private final ObjectMapper mapper;
    private final MetricRegistry registry;
    private final AWSDatabaseHolder dbHolder;

    public Handler(MetricRegistry registry, AWSDatabaseHolder dbHolder) {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JodaModule());
        this.registry = registry;
        this.dbHolder = dbHolder;
    }

    @Override
    public void handle(String target,
                       Request baseRequest,
                       HttpServletRequest request,
                       HttpServletResponse response) {
        try {
            final Map<String, String[]> paramMap = request.getParameterMap();

            switch (target) {
                case "/ec2":
                    handleEC2(response, paramMap);
                    break;
                case "/iam":
                    handleIAM(response);
                default:
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        } finally {
            baseRequest.setHandled(true);
        }
    }

    private void handleIAM(HttpServletResponse response) {
        try {
            mapper.writer().writeValue(
                    response.getOutputStream(),
                    dbHolder.getCurrent().getAccessKeyMetadata());
        } catch (IOException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            log.error("I/O error handling IAM request", e);
        }
    }

    private void handleEC2(HttpServletResponse response,
                           Map<String, String[]> params) {
        final String query = getQuery(params);
        final String sort = getSort(params);
        final int limit = getLimit(params);
        final Set<String> fields = getFields(params);

        response.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");

        try {
            try {
                final List<EC2Instance> queriedInstances = listInstancesFromQueryExpression(query);
                final List<EC2Instance> sortedInstances = sortInstancesWithExpression(queriedInstances, sort);
                final List<EC2Instance> servedInstances;

                if (limit >= 0)
                    servedInstances = sortedInstances.subList(0, limit);
                else
                    servedInstances = sortedInstances;

                if (servedInstances.size() == 0) {
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                } else {
                    final ServletOutputStream outputStream = response.getOutputStream();
                    final SimpleFilterProvider filterProvider = new SimpleFilterProvider();
                    if (fields != null) {
                        log.debug("filtered output ({})", fields);
                        filterProvider.addFilter("InstanceFilter",
                                SimpleBeanPropertyFilter.filterOutAllExcept(fields));
                    } else {
                        log.debug("unfiltered output");
                        filterProvider.addFilter("InstanceFilter",
                                SimpleBeanPropertyFilter.serializeAllExcept());
                    }
                    mapper.writer(filterProvider).writeValue(outputStream, servedInstances);
                }
            } catch (Exception e) {
                final ServletOutputStream outputStream = response.getOutputStream();
                outputStream.print(e.toString());
                outputStream.close();
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
        } catch (IOException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            log.error("I/O error handling EC2 request", e);
        }
    }

    private List<EC2Instance> listInstancesFromQueryExpression(String expression)
            throws OgnlException {
        final List<EC2Instance> allInstances = dbHolder.getCurrent().getInstances();
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

    private List<EC2Instance> sortInstancesWithExpression(List<EC2Instance> instances, String expression) throws OgnlException {
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
        return -1;
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
