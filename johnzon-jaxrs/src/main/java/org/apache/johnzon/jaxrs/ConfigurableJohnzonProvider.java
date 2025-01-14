/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.johnzon.jaxrs;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.json.JsonReaderFactory;
import javax.json.stream.JsonGeneratorFactory;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;

import org.apache.johnzon.mapper.MapperBuilder;
import org.apache.johnzon.mapper.SerializeValueFilter;
import org.apache.johnzon.mapper.access.AccessMode;
import org.apache.johnzon.mapper.access.BaseAccessMode;

// @Provider // don't let it be scanned, it would conflict with JohnzonProvider
@Produces("application/json")
@Consumes("application/json")
public class ConfigurableJohnzonProvider<T> implements MessageBodyWriter<T>, MessageBodyReader<T> {
    // build/configuration
    private MapperBuilder builder = new MapperBuilder();
    private List<String> ignores;

    // runtime
    private AtomicReference<JohnzonProvider<T>> delegate = new AtomicReference<JohnzonProvider<T>>();

    private JohnzonProvider<T> instance() {
        JohnzonProvider<T> instance;
        do {
            instance = delegate.get();
            if (builder != null && delegate.compareAndSet(null, new JohnzonProvider<T>(builder.build(), ignores))) {
                // reset build instances
                builder = null;
                ignores = null;
            }
        } while (instance == null);
        return instance;
    }

    @Override
    public boolean isReadable(final Class<?> rawType, final Type genericType,
                              final Annotation[] annotations, final MediaType mediaType) {
        return instance().isReadable(rawType, genericType, annotations, mediaType);
    }

    @Override
    public T readFrom(final Class<T> rawType, final Type genericType,
                      final Annotation[] annotations, final MediaType mediaType,
                      final MultivaluedMap<String, String> httpHeaders,
                      final InputStream entityStream) throws IOException {
        return instance().readFrom(rawType, genericType, annotations, mediaType, httpHeaders, entityStream);
    }

    @Override
    public long getSize(final T t, final Class<?> rawType, final Type genericType,
                        final Annotation[] annotations, final MediaType mediaType) {
        return instance().getSize(t, rawType, genericType, annotations, mediaType);
    }

    @Override
    public boolean isWriteable(final Class<?> rawType, final Type genericType,
                               final Annotation[] annotations, final MediaType mediaType) {
        return instance().isWriteable(rawType, genericType, annotations, mediaType);
    }

    @Override
    public void writeTo(final T t, final Class<?> rawType, final Type genericType,
                        final Annotation[] annotations, final MediaType mediaType,
                        final MultivaluedMap<String, Object> httpHeaders,
                        final OutputStream entityStream) throws IOException {
        instance().writeTo(t, rawType, genericType, annotations, mediaType, httpHeaders, entityStream);
    }

    public void setSnippetMaxLength(final int value) {
        builder.setSnippetMaxLength(value);
    }

    public void setUseJsRange(final boolean value) {
        builder.setUseJsRange(value);
    }

    public void setUseBigDecimalForObjectNumbers(final boolean value) {
        builder.setUseBigDecimalForObjectNumbers(value);
    }

    // type=a,b,c|type2=d,e
    public void setIgnoreFieldsForType(final String mapping) {
        for (final String config : mapping.split(" *| *")) {
            final String[] parts = config.split(" *= *");
            try {
                final Class<?> type = Thread.currentThread().getContextClassLoader().loadClass(parts[0]);
                if (parts.length == 1) {
                    builder.setIgnoreFieldsForType(type);
                } else {
                    builder.setIgnoreFieldsForType(type, parts[1].split(" *, *"));
                }
            } catch (final ClassNotFoundException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    public void setFailOnUnknownProperties(final boolean active) {
        builder.setFailOnUnknownProperties(active);
    }

    public void setPolymorphicSerializationPredicate(final String classes) {
        final Set<Class<?>> set = asSet(classes);
        builder.setPolymorphicSerializationPredicate(set::contains);
    }

    public void setPolymorphicDeserializationPredicate(final String classes) {
        final Set<Class<?>> set = asSet(classes);
        builder.setPolymorphicDeserializationPredicate(set::contains);
    }

    public void setPolymorphicDiscriminatorMapper(final Map<String, String> discriminatorMapper) {
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        final Map<Class<?>, String> map = discriminatorMapper.entrySet().stream()
                .collect(toMap(e -> {
                    try {
                        return loader.loadClass(e.getKey().trim());
                    } catch (final ClassNotFoundException ex) {
                        throw new IllegalArgumentException(ex);
                    }
                }, Map.Entry::getValue));
        builder.setPolymorphicDiscriminatorMapper(map::get);
    }

    public void setPolymorphicTypeLoader(final Map<String, String> aliasTypeMapping) {
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        final Map<String, Class<?>> map = aliasTypeMapping.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> {
                    try {
                        return loader.loadClass(e.getValue().trim());
                    } catch (final ClassNotFoundException ex) {
                        throw new IllegalArgumentException(ex);
                    }
                }));
        builder.setPolymorphicTypeLoader(map::get);
    }

    public void setPolymorphicDiscriminator(final String value) {
        builder.setPolymorphicDiscriminator(value);
    }

    public void setSupportConstructors(final boolean supportConstructors) {
        builder.setSupportConstructors(supportConstructors);
    }

    public void setPretty(final boolean pretty) {
        builder.setPretty(pretty);
    }

    public void setSupportGetterForCollections(final boolean supportGetterForCollections) {
        builder.setSupportGetterForCollections(supportGetterForCollections);
    }

    public void setSupportsComments(final boolean supportsComments) {
        builder.setSupportsComments(supportsComments);
    }

    public void setIgnores(final String ignores) {
        this.ignores = ignores == null ? null : asList(ignores.split(" *, *"));
    }

    public void setAccessMode(final AccessMode mode) {
        builder.setAccessMode(mode);
    }

    public void setAccessModeName(final String mode) {
        builder.setAccessModeName(mode);
    }

    public void setAccessModeFieldFilteringStrategy(final BaseAccessMode.FieldFilteringStrategy strategy) {
        builder.setAccessModeFieldFilteringStrategy(strategy);
    }

    public void setInterfaceImplementationMapping(final Map<String, String> interfaceImplementationMapping) {
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        final Function<String, Class<?>> load = name -> {
            try {
                return loader.loadClass(name.trim());
            } catch (final ClassNotFoundException e) {
                throw new IllegalArgumentException(e);
            }
        };
        builder.setInterfaceImplementationMapping(interfaceImplementationMapping.entrySet().stream()
            .collect(toMap(it -> load.apply(it.getKey()), it -> load.apply(it.getValue()))));
    }

    public void setAccessModeFieldFilteringStrategyName(final String mode) {
        builder.setAccessModeFieldFilteringStrategyName(mode);
    }

    public void setSupportHiddenAccess(final boolean supportHiddenAccess) {
        builder.setSupportHiddenAccess(supportHiddenAccess);
    }

    public void setAttributeOrder(final Comparator<String> attributeOrder) {
        builder.setAttributeOrder(attributeOrder);
    }

    public void setReaderFactory(final JsonReaderFactory readerFactory) {
        builder.setReaderFactory(readerFactory);
    }

    public void setGeneratorFactory(final JsonGeneratorFactory generatorFactory) {
        builder.setGeneratorFactory(generatorFactory);
    }

    public void setDoCloseOnStreams(final boolean doCloseOnStreams) {
        builder.setDoCloseOnStreams(doCloseOnStreams);
    }

    public void setVersion(final int version) {
        builder.setVersion(version);
    }

    public void setSkipNull(final boolean skipNull) {
        builder.setSkipNull(skipNull);
    }

    public void setSkipEmptyArray(final boolean skipEmptyArray) {
        builder.setSkipEmptyArray(skipEmptyArray);
    }

    public void setBufferSize(final int bufferSize) {
        builder.setBufferSize(bufferSize);
    }

    public void setBufferStrategy(final String bufferStrategy) {
        builder.setBufferStrategy(bufferStrategy);
    }

    public void setMaxSize(final int size) {
        builder.setMaxSize(size);
    }

    public void setTreatByteArrayAsBase64(final boolean treatByteArrayAsBase64) {
        builder.setTreatByteArrayAsBase64(treatByteArrayAsBase64);
    }

    public void setEncoding(final String encoding) {
        builder.setEncoding(encoding);
    }

    public void setReadAttributeBeforeWrite(final boolean rabw) {
        builder.setReadAttributeBeforeWrite(rabw);
    }

    public void setPrimitiveConverters(final boolean val) {
        builder.setPrimitiveConverters(val);
    }

    public MapperBuilder setDeduplicateObjects(boolean deduplicateObjects) {
        return builder.setDeduplicateObjects(deduplicateObjects);
    }

    public void setSerializeValueFilter(final String val) {
        try {
            builder.setSerializeValueFilter(SerializeValueFilter.class.cast(
                    Thread.currentThread().getContextClassLoader().loadClass(val).getConstructor().newInstance()));
        } catch (final InstantiationException | IllegalAccessException | NoSuchMethodException | ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException(e.getCause());
        }
    }

    public void setUseBigDecimalForFloats(final boolean useBigDecimalForFloats) {
        builder.setUseBigDecimalForFloats(useBigDecimalForFloats);
    }

    public void setAutoAdjustStringBuffers(final boolean autoAdjustStringBuffers) {
        builder.setAutoAdjustStringBuffers(autoAdjustStringBuffers);
    }

    private Set<Class<?>> asSet(final String classes) {
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return Stream.of(classes.split(" *, *"))
                .map(n -> {
                    try {
                        return loader.loadClass(n.trim());
                    } catch (final ClassNotFoundException ex) {
                        throw new IllegalArgumentException(ex);
                    }
                }).collect(toSet());
    }
}
