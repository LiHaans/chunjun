/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.dtstack.flinkx.metrics;

import com.dtstack.flinkx.constants.Metrics;
import com.dtstack.flinkx.util.SysUtil;
import org.apache.flink.api.common.accumulators.LongCounter;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.configuration.TaskManagerOptions;
import org.apache.flink.metrics.MetricGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;


/**
 * @author jiangbo
 * @date 2019/6/5
 */
public class BaseMetric {

    protected final Logger LOG = LoggerFactory.getLogger(getClass());

    private final static Long DEFAULT_PERIOD_MILLISECONDS = 10000L;

    private Long delayPeriodMill;

    private MetricGroup flinkxOutput;

    private String sourceName;

    private long totalWaitMill = 0;

    private long maxWaitMill;

    private final Map<String, LongCounter> metricCounters = new HashMap<>();

    public BaseMetric(RuntimeContext runtimeContext, String sourceName, boolean isLocal) {
        this.sourceName = sourceName;

        maxWaitMill = TaskManagerOptions.TASK_CANCELLATION_INTERVAL.defaultValue();
        flinkxOutput = runtimeContext.getMetricGroup().addGroup(Metrics.METRIC_GROUP_KEY_FLINKX, Metrics.METRIC_GROUP_VALUE_OUTPUT);

        if (isLocal){
            delayPeriodMill = 0L;
        } else {
            if(sourceName.contains("writer")){
                delayPeriodMill = (long)(DEFAULT_PERIOD_MILLISECONDS * 2.5);
            } else {
                delayPeriodMill = (long)(DEFAULT_PERIOD_MILLISECONDS * 1.2);
            }
        }

        LOG.info("delayPeriodMill:[{}]", delayPeriodMill);
    }

    public void addMetric(String metricName, LongCounter counter){
        addMetric(metricName, counter, false);
    }

    public void addMetric(String metricName, LongCounter counter, boolean meterView){
        metricCounters.put(metricName, counter);
        flinkxOutput.gauge(metricName, new SimpleAccumulatorGauge<Long>(counter));
        if (meterView){
            flinkxOutput.meter(metricName + Metrics.SUFFIX_RATE, new SimpleLongCounterMeterView(counter, 60));
        }
    }

    public Map<String, LongCounter> getMetricCounters() {
        return metricCounters;
    }

    public void waitForReportMetrics(){
        if(delayPeriodMill == 0){
            return;
        }

        if(totalWaitMill + delayPeriodMill > maxWaitMill){
            return;
        }

        try {
            Thread.sleep(delayPeriodMill);
            LOG.info("Wait [{}] millisecond for [{}]", delayPeriodMill, sourceName);

            totalWaitMill += delayPeriodMill;
            LOG.info("wait [{}] mill for source [{}]", totalWaitMill, sourceName);
        } catch (InterruptedException e){
            SysUtil.sleep(delayPeriodMill);
            totalWaitMill += delayPeriodMill;
            LOG.info("Task [{}] thread is interrupted,wait [{}] mill for source [{}]", sourceName, totalWaitMill, sourceName);
        }
    }
}