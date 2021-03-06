/**
 * Copyright 2014 NAVER Corp.
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
package com.navercorp.pinpoint.bootstrap.plugin;

import java.lang.instrument.ClassFileTransformer;

import com.navercorp.pinpoint.bootstrap.config.ProfilerConfig;
import com.navercorp.pinpoint.bootstrap.instrument.ByteCodeInstrumentor;
import com.navercorp.pinpoint.bootstrap.plugin.transformer.ClassFileTransformerBuilder;
import com.navercorp.pinpoint.bootstrap.plugin.transformer.PinpointClassFileTransformer;

/**
 *  Provides attributes and objects to interceptors.
 * 
 *  Only interceptors can acquire an instance of this class as a constructor argument.
 * 
 * @author Jongho Moon
 *
 */
public interface ProfilerPluginSetupContext {
    /**
     * Get the {@link ProfilerConfig}
     * 
     * @return {@link ProfilerConfig}
     */
    public ProfilerConfig getConfig();

    /**
     * Add a {@link ApplicationTypeDetector} to Pinpoint agent.
     * 
     * @param detectors
     */
    public void addApplicationTypeDetector(ApplicationTypeDetector... detectors);
    
    public void addClassFileTransformer(String targetClassName, PinpointClassFileTransformer transformer);


    
    
    /**
     * Add a {@link ClassEditor} to Pinpoint agent.
     * 
     * @param classEditor
     */
    @Deprecated

    public void addClassFileTransformer(ClassFileTransformer transformer);
    /**
     * Get {@link ByteCodeInstrumentor}
     * 
     * @return {@link ByteCodeInstrumentor}
     */
    @Deprecated
    public ByteCodeInstrumentor getByteCodeInstrumentor();
    
    /**
     * Get a {@link ClassFileTransformerBuilder}.
     * 
     * By using returned {@link ClassFileTransformerBuilder} you can create a {@link ClassFileTransformer} easily.
     * You have to register resulting {@link ClassFileTransformer} by {@link #addClassFileTransformer(ClassFileTransformer)} to make it works.
     *
     * @param targetClassName target class name
     * @return {@link ClassFileTransformerBuilder}
     */
    @Deprecated
    public ClassFileTransformerBuilder getClassFileTransformerBuilder(String targetClassName);
}
