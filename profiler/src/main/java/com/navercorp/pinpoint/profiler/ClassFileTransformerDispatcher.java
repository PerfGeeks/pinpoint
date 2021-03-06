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

package com.navercorp.pinpoint.profiler;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.navercorp.pinpoint.bootstrap.config.ProfilerConfig;
import com.navercorp.pinpoint.bootstrap.instrument.ByteCodeInstrumentor;
import com.navercorp.pinpoint.bootstrap.instrument.DynamicTransformRequestListener;
import com.navercorp.pinpoint.bootstrap.plugin.transformer.MatchableClassFileTransformer;
import com.navercorp.pinpoint.profiler.modifier.AbstractModifier;
import com.navercorp.pinpoint.profiler.modifier.DefaultModifierRegistry;
import com.navercorp.pinpoint.profiler.modifier.ModifierRegistry;
import com.navercorp.pinpoint.profiler.plugin.ClassFileTransformerAdaptor;
import com.navercorp.pinpoint.profiler.plugin.DefaultProfilerPluginContext;
import com.navercorp.pinpoint.profiler.util.JavaAssistUtils;

/**
 * @author emeroad
 * @author netspider
 */
public class ClassFileTransformerDispatcher implements ClassFileTransformer, DynamicTransformRequestListener {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final boolean isDebug = logger.isDebugEnabled();

    private final ClassLoader agentClassLoader = this.getClass().getClassLoader();

    private final ModifierRegistry modifierRegistry;

    private final DefaultAgent agent;
    private final ByteCodeInstrumentor byteCodeInstrumentor;
    private final DynamicTrnasformerRegistry dynamicTransformerRegistry;

    private final ProfilerConfig profilerConfig;

    private final ClassFileFilter skipFilter;
    
    public ClassFileTransformerDispatcher(DefaultAgent agent, ByteCodeInstrumentor byteCodeInstrumentor, List<DefaultProfilerPluginContext> pluginContexts) {
        if (agent == null) {
            throw new NullPointerException("agent must not be null");
        }
        if (byteCodeInstrumentor == null) {
            throw new NullPointerException("byteCodeInstrumentor must not be null");
        }

        
        this.agent = agent;
        this.byteCodeInstrumentor = byteCodeInstrumentor;
        this.dynamicTransformerRegistry = new DefaultDynamicTransformerRegistry();
        this.profilerConfig = agent.getProfilerConfig();
        this.modifierRegistry = createModifierRegistry(pluginContexts);
        this.skipFilter = new DefaultClassFileFilter(agentClassLoader);
    }

    @Override
    public byte[] transform(ClassLoader classLoader, String jvmClassName, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classFileBuffer) throws IllegalClassFormatException {
        ClassFileTransformer transformer = dynamicTransformerRegistry.getTransformer(classLoader, jvmClassName);
        
        if (transformer != null) {
            return transformUsingTransformer(classLoader, jvmClassName, classBeingRedefined, protectionDomain, classFileBuffer, transformer);
        }
        
        if (skipFilter.doFilter(classLoader, jvmClassName, classBeingRedefined, protectionDomain, classFileBuffer)) {
            return null;
        }

        AbstractModifier findModifier = this.modifierRegistry.findModifier(jvmClassName);
        
        if (findModifier == null) {
            // For debug
            // TODO What if a modifier is duplicated?
            if (this.profilerConfig.getProfilableClassFilter().filter(jvmClassName)) {
                // Added to see if call stack view is OK on a test machine.
                findModifier = this.modifierRegistry.findModifier("*");
            } else {
                return null;
            }
        }

        return transformUsingModifier(classLoader, jvmClassName, protectionDomain, classFileBuffer, findModifier);
    }

    private byte[] transformUsingModifier(ClassLoader classLoader, String jvmClassName, ProtectionDomain protectionDomain, byte[] classFileBuffer, AbstractModifier findModifier) {
        if (isDebug) {
            logger.debug("[transform] cl:{} className:{} Modifier:{}", classLoader, jvmClassName, findModifier.getClass().getName());
        }
        final String javaClassName = JavaAssistUtils.jvmNameToJavaName(jvmClassName);

        try {
            final Thread thread = Thread.currentThread();
            final ClassLoader before = getContextClassLoader(thread);
            thread.setContextClassLoader(this.agentClassLoader);
            try {
                return findModifier.modify(classLoader, javaClassName, protectionDomain, classFileBuffer);
            } finally {
                // The context class loader have to be recovered even if it was null.
                thread.setContextClassLoader(before);
            }
        }
        catch (Throwable e) {
            logger.error("Modifier:{} modify fail. cl:{} ctxCl:{} agentCl:{} Cause:{}",
                    findModifier.getMatcher(), classLoader, Thread.currentThread().getContextClassLoader(), agentClassLoader, e.getMessage(), e);
            return null;
        }
    }

    private byte[] transformUsingTransformer(ClassLoader classLoader, String jvmClassName, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classFileBuffer, ClassFileTransformer transformer) {
        final String javaClassName = JavaAssistUtils.jvmNameToJavaName(jvmClassName);

        if (isDebug) {
            if (classBeingRedefined == null) {
                logger.debug("[transform] classLoader:{} className:{} trnasformer:{}", classLoader, javaClassName, transformer.getClass().getName());
            } else {
                logger.debug("[retransform] classLoader:{} className:{} trnasformer:{}", classLoader, javaClassName, transformer.getClass().getName());
            }
        }

        try {
            final Thread thread = Thread.currentThread();
            final ClassLoader before = getContextClassLoader(thread);
            thread.setContextClassLoader(this.agentClassLoader);
            try {
                return transformer.transform(classLoader, javaClassName, null, protectionDomain, classFileBuffer);
            } finally {
                // The context class loader have to be recovered even if it was null.
                thread.setContextClassLoader(before);
            }
        } catch (Throwable e) {
            logger.error("Transformer:{} threw an exception. cl:{} ctxCl:{} agentCl:{} Cause:{}",
                    transformer.getClass().getName(), classLoader, Thread.currentThread().getContextClassLoader(), agentClassLoader, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public void onRetransformRequest(Class<?> target, final ClassFileTransformer transformer) {
        this.dynamicTransformerRegistry.onRetransformRequest(target, transformer);
    }

    @Override
    public void onTransformRequest(ClassLoader classLoader, String targetClassName, ClassFileTransformer transformer) {
        this.dynamicTransformerRegistry.onTransformRequest(classLoader, targetClassName, transformer);
    }

    private ClassLoader getContextClassLoader(Thread thread) throws Throwable {
        try {
            return thread.getContextClassLoader();
        } catch (SecurityException se) {
            throw se;
        } catch (Throwable th) {
            if (isDebug) {
                logger.debug("getContextClassLoader(). Caused:{}", th.getMessage(), th);
            }
            throw th;
        }
    }

    private ModifierRegistry createModifierRegistry(List<DefaultProfilerPluginContext> pluginContexts) {
        DefaultModifierRegistry modifierRepository = new DefaultModifierRegistry(agent, byteCodeInstrumentor);

        modifierRepository.addMethodModifier();

        // logback
        modifierRepository.addLogbackModifier();
        
        loadEditorsFromPlugins(modifierRepository, pluginContexts);
        
        return modifierRepository;
    }

    private void loadEditorsFromPlugins(DefaultModifierRegistry modifierRepository, List<DefaultProfilerPluginContext> pluginContexts) {
        for (DefaultProfilerPluginContext pluginContext : pluginContexts) {
            for (ClassFileTransformer transformer : pluginContext.getClassEditors()) {
                if (transformer instanceof MatchableClassFileTransformer) {
                    MatchableClassFileTransformer t = (MatchableClassFileTransformer)transformer;
                    logger.info("Registering class file transformer {} for {} ", t, t.getMatcher());
                    modifierRepository.addModifier(new ClassFileTransformerAdaptor(byteCodeInstrumentor, t));
                } else {
                    logger.warn("Ignore class file transformer {}", transformer);
                }
            }
        }
    }

}
