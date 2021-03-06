/*
 * Copyright 2014 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.plugin.commons.dbcp.interceptor;

import com.navercorp.pinpoint.bootstrap.context.SpanEventRecorder;
import com.navercorp.pinpoint.bootstrap.context.TraceContext;
import com.navercorp.pinpoint.bootstrap.interceptor.MethodDescriptor;
import com.navercorp.pinpoint.bootstrap.interceptor.SpanEventSimpleAroundInterceptorForPlugin;
import com.navercorp.pinpoint.bootstrap.plugin.annotation.Group;
import com.navercorp.pinpoint.bootstrap.plugin.annotation.TargetMethod;
import com.navercorp.pinpoint.bootstrap.plugin.annotation.Targets;
import com.navercorp.pinpoint.plugin.commons.dbcp.CommonsDbcpPlugin;

/**
 * Maybe we should trace get of Datasource.
 * @author emeroad
 */
@Group(CommonsDbcpPlugin.DBCP_GROUP)
@Targets(methods={
        @TargetMethod(name="getConnection"),
        @TargetMethod(name="getConnection", paramTypes={"java.lang.String", "java.lang.String"})
})
public class DataSourceGetConnectionInterceptor extends SpanEventSimpleAroundInterceptorForPlugin {

    public DataSourceGetConnectionInterceptor(TraceContext traceContext, MethodDescriptor descriptor) {
        super(traceContext, descriptor);
    }

    @Override
    public void doInBeforeTrace(SpanEventRecorder recorder, final Object target, Object[] args) {
    }

    @Override
    public void doInAfterTrace(SpanEventRecorder recorder, Object target, Object[] args, Object result, Throwable throwable) {
        recorder.recordServiceType(CommonsDbcpPlugin.DBCP_SERVICE_TYPE);
        if (args == null) {
//          getConnection() without any arguments
            recorder.recordApi(getMethodDescriptor());
        } else if(args.length == 2) {
//          skip args[1] because it's a password.
            recorder.recordApi(getMethodDescriptor(), args[0], 0);
        }
        recorder.recordException(throwable);
    }
}
