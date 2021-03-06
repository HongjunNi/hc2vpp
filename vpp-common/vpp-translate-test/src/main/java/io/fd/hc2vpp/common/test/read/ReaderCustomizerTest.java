/*
 * Copyright (c) 2016 Cisco and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fd.hc2vpp.common.test.read;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.fd.honeycomb.translate.MappingContext;
import io.fd.honeycomb.translate.ModificationCache;
import io.fd.honeycomb.translate.read.ReadContext;
import io.fd.honeycomb.translate.spi.read.ReaderCustomizer;
import io.fd.honeycomb.translate.read.ReadContext;
import io.fd.honeycomb.translate.spi.read.ReaderCustomizer;
import io.fd.hc2vpp.common.test.util.FutureProducer;
import io.fd.hc2vpp.common.test.util.NamingContextHelper;
import java.lang.reflect.Method;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.yangtools.concepts.Builder;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import io.fd.vpp.jvpp.core.future.FutureJVppCore;

/**
 * Generic test for classes implementing {@link ReaderCustomizer} interface.
 *
 * @param <D> Specific DataObject derived type (Identifiable), that is handled by this customizer
 * @param <B> Specific Builder for handled type (D)
 */
public abstract class ReaderCustomizerTest<D extends DataObject, B extends Builder<D>> implements FutureProducer,
    NamingContextHelper {

    @Mock
    protected FutureJVppCore api;
    @Mock
    protected ReadContext ctx;
    @Mock
    protected MappingContext mappingContext;

    protected final Class<? extends Builder<? extends DataObject>> parentBuilderClass;
    protected final Class<D> dataObjectClass;
    protected ModificationCache cache;
    protected ReaderCustomizer<D, B> customizer;

    public ReaderCustomizerTest(
        final Class<D> dataObjectClass,
        final Class<? extends Builder<? extends DataObject>> parentBuilderClass) {
        this.dataObjectClass = dataObjectClass;
        this.parentBuilderClass = parentBuilderClass;
    }

    @Before
    public final void setUpParent() throws Exception {
        MockitoAnnotations.initMocks(this);
        cache = new ModificationCache();
        Mockito.doReturn(cache).when(ctx).getModificationCache();
        Mockito.doReturn(mappingContext).when(ctx).getMappingContext();
        setUp();
        customizer = initCustomizer();
    }

    /**
     * Optional setup for subclasses. Invoked before customizer is initialized.
     */
    protected void setUp() throws Exception {
    }

    protected abstract ReaderCustomizer<D, B> initCustomizer();

    protected ReaderCustomizer<D, B> getCustomizer() {
        return customizer;
    }

    @Test
    public void testGetBuilder() throws Exception {
        assertNotNull(customizer.getBuilder(InstanceIdentifier.create(dataObjectClass)));
    }

    @Test
    public void testMerge() throws Exception {
        final Builder<? extends DataObject> parentBuilder = mock(parentBuilderClass);
        final D value = mock(dataObjectClass);
        getCustomizer().merge(parentBuilder, value);

        final Method method = parentBuilderClass.getMethod("set" + dataObjectClass.getSimpleName(), dataObjectClass);
        method.invoke(verify(parentBuilder), value);
    }
}
