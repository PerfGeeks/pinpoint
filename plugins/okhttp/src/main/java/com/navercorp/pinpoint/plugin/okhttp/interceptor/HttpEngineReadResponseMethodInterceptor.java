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
package com.navercorp.pinpoint.plugin.okhttp.interceptor;

import com.navercorp.pinpoint.bootstrap.context.*;
import com.navercorp.pinpoint.bootstrap.interceptor.MethodDescriptor;
import com.navercorp.pinpoint.bootstrap.interceptor.SimpleAroundInterceptor;
import com.navercorp.pinpoint.bootstrap.interceptor.group.InterceptorGroup;
import com.navercorp.pinpoint.bootstrap.logging.PLogger;
import com.navercorp.pinpoint.bootstrap.logging.PLoggerFactory;
import com.navercorp.pinpoint.bootstrap.sampler.SamplingFlagUtils;
import com.navercorp.pinpoint.common.trace.AnnotationKey;
import com.navercorp.pinpoint.plugin.okhttp.ConnectionGetter;
import com.navercorp.pinpoint.plugin.okhttp.OkHttpConstants;
import com.navercorp.pinpoint.plugin.okhttp.UserRequestGetter;
import com.navercorp.pinpoint.plugin.okhttp.UserResponseGetter;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

/**
 * @author jaehong.kim
 */
public class HttpEngineReadResponseMethodInterceptor implements SimpleAroundInterceptor, OkHttpConstants {
    private final PLogger logger = PLoggerFactory.getLogger(this.getClass());
    private final boolean isDebug = logger.isDebugEnabled();

    private TraceContext traceContext;
    private MethodDescriptor methodDescriptor;
    private InterceptorGroup interceptorGroup;
    private final boolean statusCode;

    public HttpEngineReadResponseMethodInterceptor(TraceContext traceContext, MethodDescriptor methodDescriptor, boolean statusCode) {
        this.traceContext = traceContext;
        this.methodDescriptor = methodDescriptor;
        this.statusCode = statusCode;
    }

    @Override
    public void before(Object target, Object[] args) {
        if (isDebug) {
            logger.beforeInterceptor(target, args);
        }

        final Trace trace = traceContext.currentTraceObject();
        if (trace == null) {
            return;
        }

        if (!validate(target)) {
            return;
        }

        SpanEventRecorder recorder = trace.traceBlockBegin();
        recorder.recordServiceType(OK_HTTP_CLIENT_INTERNAL);
    }

    private boolean validate(Object target) {
        if (!(target instanceof UserRequestGetter)) {
            logger.debug("Invalid target object. Need field accessor({}).", FIELD_USER_REQUEST);
            return false;
        }

        if (!(target instanceof UserResponseGetter)) {
            logger.debug("Invalid target object. Need field accessor({}).", FIELD_USER_RESPONSE);
            return false;
        }

        if (!(target instanceof ConnectionGetter)) {
            logger.debug("Invalid target object. Need field accessor({}).", FIELD_CONNECTION);
            return false;
        }

        return true;
    }

    @Override
    public void after(Object target, Object[] args, Object result, Throwable throwable) {
        if (isDebug) {
            logger.afterInterceptor(target, args);
        }

        final Trace trace = traceContext.currentTraceObject();
        if (trace == null) {
            return;
        }

        if (!validate(target)) {
            return;
        }

        try {
            SpanEventRecorder recorder = trace.currentSpanEventRecorder();
            recorder.recordApi(methodDescriptor);
            recorder.recordException(throwable);

            if (statusCode) {
                Response response = ((UserResponseGetter) target)._$PINPOINT$_getUserResponse();
                if (response != null) {
                    recorder.recordAttribute(AnnotationKey.HTTP_STATUS_CODE, response.code());
                }
            }
        } finally {
            trace.traceBlockEnd();
        }
    }
}