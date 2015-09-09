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
package com.navercorp.pinpoint.plugin.okhttp;

import java.security.ProtectionDomain;

import com.navercorp.pinpoint.bootstrap.instrument.*;
import com.navercorp.pinpoint.bootstrap.interceptor.AsyncTraceIdAccessor;
import com.navercorp.pinpoint.bootstrap.logging.PLogger;
import com.navercorp.pinpoint.bootstrap.logging.PLoggerFactory;
import com.navercorp.pinpoint.bootstrap.plugin.ProfilerPlugin;
import com.navercorp.pinpoint.bootstrap.plugin.ProfilerPluginInstrumentContext;
import com.navercorp.pinpoint.bootstrap.plugin.ProfilerPluginSetupContext;
import com.navercorp.pinpoint.bootstrap.plugin.transformer.PinpointClassFileTransformer;

/**
 * @author jaehong.kim
 *
 */
public class OkHttpPlugin implements ProfilerPlugin, OkHttpConstants {
    private final PLogger logger = PLoggerFactory.getLogger(this.getClass());

    @Override
    public void setup(ProfilerPluginSetupContext context) {
        final OkHttpPluginConfig config = new OkHttpPluginConfig(context.getConfig());
        logger.debug("[OkHttp] Initialized config={}", config);

        logger.debug("[OkHttp] Add Call class.");
        addCall(context, config);
        logger.debug("[OkHttp] Add Dispatcher class.");
        addDispatcher(context, config);
        logger.debug("[OkHttp] Add AsyncCall class.");
        addAsyncCall(context, config);
        addHttpEngine(context, config);
        addRequestBuilder(context, config);
    }

    private void addCall(ProfilerPluginSetupContext context, final OkHttpPluginConfig config) {
        context.addClassFileTransformer("com.squareup.okhttp.Call", new PinpointClassFileTransformer() {

            @Override
            public byte[] transform(ProfilerPluginInstrumentContext instrumentContext, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws InstrumentException {
                InstrumentClass target = instrumentContext.getInstrumentClass(loader, className, classfileBuffer);

                for (InstrumentMethod method : target.getDeclaredMethods(MethodFilters.name("execute", "enqueue", "cancel"))) {
                    method.addInterceptor(BASIC_METHOD_INTERCEPTOR, OK_HTTP_CLIENT_INTERNAL);
                }

                return target.toBytecode();
            }
        });
    }

    private void addDispatcher(ProfilerPluginSetupContext context, final OkHttpPluginConfig config) {
        context.addClassFileTransformer("com.squareup.okhttp.Dispatcher", new PinpointClassFileTransformer() {

            @Override
            public byte[] transform(ProfilerPluginInstrumentContext instrumentContext, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws InstrumentException {
                InstrumentClass target = instrumentContext.getInstrumentClass(loader, className, classfileBuffer);

                for(InstrumentMethod method : target.getDeclaredMethods(MethodFilters.name("execute", "cancel"))) {
                    logger.debug("[OkHttp] Add Dispatcher.execute | cancel interceptor.");
                    method.addInterceptor(BASIC_METHOD_INTERCEPTOR, OK_HTTP_CLIENT_INTERNAL);
                }
                InstrumentMethod enqueueMethod = target.getDeclaredMethod("enqueue", "com.squareup.okhttp.Call$AsyncCall");
                if(enqueueMethod != null) {
                    logger.debug("[OkHttp] Add Dispatcher.enqueue interceptor.");
                    enqueueMethod.addInterceptor("com.navercorp.pinpoint.plugin.okhttp.interceptor.DispatcherEnqueueMethodInterceptor");
                }

                return target.toBytecode();
            }
        });
    }

    private void addAsyncCall(ProfilerPluginSetupContext context, final OkHttpPluginConfig config) {
        context.addClassFileTransformer("com.squareup.okhttp.Call$AsyncCall", new PinpointClassFileTransformer() {

            @Override
            public byte[] transform(ProfilerPluginInstrumentContext instrumentContext, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws InstrumentException {
                InstrumentClass target = instrumentContext.getInstrumentClass(loader, className, classfileBuffer);
                target.addField(AsyncTraceIdAccessor.class.getName());

                InstrumentMethod executeMethod = target.getDeclaredMethod("execute");
                if(executeMethod != null) {
                    logger.debug("[OkHttp] Add AsyncCall.execute interceptor.");
                    executeMethod.addInterceptor("com.navercorp.pinpoint.plugin.okhttp.interceptor.AsyncCallExecuteMethodInterceptor");
                }

                return target.toBytecode();
            }
        });
    }

    private void addHttpEngine(ProfilerPluginSetupContext context, final OkHttpPluginConfig config) {
        context.addClassFileTransformer("com.squareup.okhttp.internal.http.HttpEngine", new PinpointClassFileTransformer() {

            @Override
            public byte[] transform(ProfilerPluginInstrumentContext instrumentContext, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws InstrumentException {
                InstrumentClass target = instrumentContext.getInstrumentClass(loader, className, classfileBuffer);
                target.addGetter(UserRequestGetter.class.getName(), FIELD_USER_REQUEST);
                target.addGetter(UserResponseGetter.class.getName(), FIELD_USER_RESPONSE);
                target.addGetter(ConnectionGetter.class.getName(), FIELD_CONNECTION);

                InstrumentMethod sendRequestMethod = target.getDeclaredMethod("sendRequest");
                if(sendRequestMethod != null) {
                    logger.debug("[OkHttp] Add HttpEngine.sendRequest interceptor.");
                    sendRequestMethod.addInterceptor("com.navercorp.pinpoint.plugin.okhttp.interceptor.HttpEngineSendRequestMethodInterceptor");
                }

                InstrumentMethod connectMethod = target.getDeclaredMethod("connect");
                if(connectMethod != null) {
                    logger.debug("[OkHttp] Add HttpEngine.connect interceptor.");
                    connectMethod.addInterceptor("com.navercorp.pinpoint.plugin.okhttp.interceptor.HttpEngineConnectMethodInterceptor");
                }

                InstrumentMethod readResponseMethod = target.getDeclaredMethod("readResponse");
                if(readResponseMethod != null) {
                    logger.debug("[OkHttp] Add HttpEngine.connect interceptor.");
                    readResponseMethod.addInterceptor("com.navercorp.pinpoint.plugin.okhttp.interceptor.HttpEngineReadResponseMethodInterceptor");
                }

                return target.toBytecode();
            }
        });
    }

    private void addRequestBuilder(ProfilerPluginSetupContext context, final OkHttpPluginConfig config) {
        context.addClassFileTransformer("com.squareup.okhttp.Request$Builder", new PinpointClassFileTransformer() {

            @Override
            public byte[] transform(ProfilerPluginInstrumentContext instrumentContext, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws InstrumentException {
                InstrumentClass target = instrumentContext.getInstrumentClass(loader, className, classfileBuffer);
                target.addGetter(HttpUrlGetter.class.getName(), FIELD_HTTP_URL);

                InstrumentMethod buildMethod = target.getDeclaredMethod("build");
                if(buildMethod != null) {
                    logger.debug("[OkHttp] Add Request.Builder.build interceptor.");
                    buildMethod.addInterceptor("com.navercorp.pinpoint.plugin.okhttp.interceptor.RequestBuilderBuildMethodInterceptor");
                }

                return target.toBytecode();
            }
        });
    }

}